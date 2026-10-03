package detran;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Cliente implements MqttCallback {

    private final MqttClient mqtt;
    private final String uf;
    private final String topicoResposta;
    private final Map<String, CompletableFuture<JsonObject>> aguardando = new ConcurrentHashMap<>();

    public Cliente(String uf) throws Exception {
        this.uf = uf.toUpperCase();
        String id = "detran-" + uf.toLowerCase() + "-" + UUID.randomUUID().toString().substring(0, 8);
        topicoResposta = Mqtt.RESPOSTAS + id;

        mqtt = new MqttClient(Mqtt.BROKER, id, new MemoryPersistence());
        mqtt.setCallback(this);
        Mqtt.conectar(mqtt, true);
        mqtt.subscribe(topicoResposta, 1);
    }

    public String getUf() {
        return uf;
    }

    public JsonObject requisitar(String topico, JsonObject requisicao) throws Exception {
        String id = UUID.randomUUID().toString();
        requisicao.addProperty("idRequisicao", id);
        // o serviço publica a resposta no tópico deste cliente
        requisicao.addProperty("responderPara", topicoResposta);
        requisicao.addProperty("detran", uf);

        CompletableFuture<JsonObject> resposta = new CompletableFuture<>();
        aguardando.put(id, resposta);
        try {
            Mqtt.publicar(mqtt, topico, requisicao);
            return resposta.get(10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            JsonObject semResposta = new JsonObject();
            semResposta.addProperty("sucesso", false);
            semResposta.addProperty("mensagem", "sem resposta em 10s, o servico pode estar fora do ar");
            return semResposta;
        } finally {
            aguardando.remove(id);
        }
    }

    @Override
    public void messageArrived(String topico, MqttMessage mensagem) {
        JsonObject resposta = JsonParser.parseString(new String(mensagem.getPayload(), StandardCharsets.UTF_8)).getAsJsonObject();
        if (resposta.has("idRequisicao") && !resposta.get("idRequisicao").isJsonNull()) {
            CompletableFuture<JsonObject> pendente = aguardando.get(resposta.get("idRequisicao").getAsString());
            if (pendente != null) {
                pendente.complete(resposta);
            }
        }
    }

    public static void imprimir(JsonObject resposta) {
        boolean sucesso = resposta.has("sucesso") && resposta.get("sucesso").getAsBoolean();
        System.out.println((sucesso ? "OK: " : "ERRO: ") + resposta.get("mensagem").getAsString());
        if (resposta.has("dados")) {
            for (JsonElement linha : resposta.getAsJsonArray("dados")) {
                List<String> campos = new ArrayList<>();
                for (Map.Entry<String, JsonElement> campo : linha.getAsJsonObject().entrySet()) {
                    campos.add(campo.getKey() + ": " + campo.getValue().getAsString());
                }
                System.out.println("  " + String.join(" | ", campos));
            }
        }
    }

    public void fechar() throws Exception {
        mqtt.disconnect();
        mqtt.close();
    }

    @Override
    public void connectionLost(Throwable causa) {
        System.out.println("conexao com o broker perdida: " + causa.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }
}
