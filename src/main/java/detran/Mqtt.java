package detran;

import com.google.gson.JsonObject;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.StandardCharsets;

public class Mqtt {

    public static final String BROKER = variavel("MQTT_BROKER", "tcp://localhost:1883");

    // requisições em denatran/<servico>/<operacao>
    public static final String VEICULOS = "denatran/veiculos/";
    public static final String CONDUTORES = "denatran/condutores/";
    public static final String MULTAS = "denatran/multas/";
    public static final String EVENTOS = "denatran/eventos/";
    public static final String RESPOSTAS = "denatran/respostas/";

    public static void conectar(MqttClient cliente, boolean sessaoLimpa) throws Exception {
        MqttConnectOptions opcoes = new MqttConnectOptions();
        opcoes.setCleanSession(sessaoLimpa);
        opcoes.setAutomaticReconnect(true);

        int tentativa = 1;
        while (true) {
            try {
                cliente.connect(opcoes);
                return;
            } catch (MqttException e) {
                if (tentativa == 30) {
                    throw e;
                }
                System.out.println("Broker MQTT indisponivel, tentando novamente em 2s (" + tentativa + ")");
                Thread.sleep(2000);
                tentativa++;
            }
        }
    }

    public static void publicar(MqttClient cliente, String topico, JsonObject conteudo) throws MqttException {
        MqttMessage mensagem = new MqttMessage(conteudo.toString().getBytes(StandardCharsets.UTF_8));
        mensagem.setQos(1); // entrega pelo menos uma vez
        cliente.publish(topico, mensagem);
    }

    public static String variavel(String nome, String padrao) {
        String valor = System.getenv(nome);
        if (valor == null || valor.isBlank()) {
            return padrao;
        }
        return valor;
    }
}
