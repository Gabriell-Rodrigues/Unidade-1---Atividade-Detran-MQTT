package detran;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.Year;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public abstract class Microsservico implements MqttCallback {

    protected final String nome;
    private final Path arquivoDados;
    private final Gson gson = new Gson();
    private final Gson gsonArquivo = new GsonBuilder().setPrettyPrinting().create();
    private MqttClient mqtt;

    // trata uma mensagem por vez, na ordem de chegada
    private final ExecutorService processador = Executors.newSingleThreadExecutor();

    protected Microsservico(String nome) {
        this.nome = nome;
        this.arquivoDados = Path.of(Mqtt.variavel("PASTA_DADOS", "dados"), nome + ".json");
    }

    protected abstract Resposta atender(String operacao, JsonObject requisicao);

    protected abstract void aoReceberEvento(String evento, JsonObject dados);

    public void iniciar(String... topicos) throws Exception {
        mqtt = new MqttClient(Mqtt.BROKER, "servico-" + nome, new MemoryPersistence());
        mqtt.setCallback(this);
        // sessão persistente: o broker guarda as mensagens enquanto o serviço estiver parado
        Mqtt.conectar(mqtt, false);
        for (String topico : topicos) {
            mqtt.subscribe(topico, 1);
        }
        System.out.println("[" + nome + "] conectado em " + Mqtt.BROKER + ", assinando " + String.join(", ", topicos));
    }

    @Override
    public void messageArrived(String topico, MqttMessage mensagem) {
        String conteudo = new String(mensagem.getPayload(), StandardCharsets.UTF_8);
        processador.submit(() -> tratar(topico, conteudo));
    }

    private void tratar(String topico, String conteudo) {
        try {
            JsonObject json = JsonParser.parseString(conteudo).getAsJsonObject();
            String ultimoNivel = topico.substring(topico.lastIndexOf('/') + 1);

            if (topico.startsWith(Mqtt.EVENTOS)) {
                aoReceberEvento(ultimoNivel, json);
                return;
            }

            Resposta resposta;
            try {
                resposta = atender(ultimoNivel, json);
            } catch (IllegalArgumentException e) {
                resposta = Resposta.erro(e.getMessage());
            }
            System.out.println("[" + nome + "] " + ultimoNivel + ": " + resposta.mensagem);

            if (json.has("responderPara")) {
                resposta.idRequisicao = json.has("idRequisicao") ? json.get("idRequisicao").getAsString() : null;
                Mqtt.publicar(mqtt, json.get("responderPara").getAsString(), gson.toJsonTree(resposta).getAsJsonObject());
            }
        } catch (Exception e) {
            System.out.println("[" + nome + "] erro ao tratar mensagem de " + topico + ": " + e);
        }
    }

    protected void publicarEvento(String evento, JsonObject dados) {
        try {
            Mqtt.publicar(mqtt, Mqtt.EVENTOS + evento, dados);
        } catch (Exception e) {
            System.out.println("[" + nome + "] falha ao publicar evento " + evento + ": " + e.getMessage());
        }
    }

    protected <T> T carregar(Class<T> tipo, T vazio) {
        try {
            if (Files.exists(arquivoDados)) {
                T dados = gsonArquivo.fromJson(Files.readString(arquivoDados), tipo);
                System.out.println("[" + nome + "] dados carregados de " + arquivoDados);
                return dados;
            }
        } catch (Exception e) {
            System.out.println("[" + nome + "] nao foi possivel ler " + arquivoDados + ": " + e.getMessage());
        }
        return vazio;
    }

    protected void salvar(Object dados) {
        try {
            Files.createDirectories(arquivoDados.getParent());
            Files.writeString(arquivoDados, gsonArquivo.toJson(dados));
        } catch (Exception e) {
            System.out.println("[" + nome + "] nao foi possivel salvar " + arquivoDados + ": " + e.getMessage());
        }
    }

    @Override
    public void connectionLost(Throwable causa) {
        System.out.println("[" + nome + "] conexao com o broker perdida, reconectando: " + causa.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    protected static String texto(JsonObject requisicao, String campo) {
        JsonElement valor = requisicao.get(campo);
        if (valor == null || valor.isJsonNull() || valor.getAsString().isBlank()) {
            throw new IllegalArgumentException("campo '" + campo + "' nao informado");
        }
        return valor.getAsString().trim();
    }

    protected static int inteiro(JsonObject requisicao, String campo) {
        String valor = texto(requisicao, campo);
        try {
            return Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("campo '" + campo + "' deve ser um numero inteiro: " + valor);
        }
    }

    protected static double numero(JsonObject requisicao, String campo) {
        String valor = texto(requisicao, campo);
        if (valor.contains(",")) {
            valor = valor.replace(".", "").replace(",", ".");
        }
        try {
            return Double.parseDouble(valor);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("campo '" + campo + "' deve ser um numero: " + valor);
        }
    }

    protected static String cpf(JsonObject requisicao, String campo) {
        String cpf = texto(requisicao, campo).replaceAll("[.\\-\\s]", "");
        if (!cpf.matches("\\d{11}")) {
            throw new IllegalArgumentException("CPF invalido: " + cpf + " (use 11 digitos)");
        }
        return cpf;
    }

    protected static String placa(JsonObject requisicao) {
        String placa = texto(requisicao, "placa").replace("-", "").toUpperCase();
        // ABC1234 ou Mercosul ABC1D23
        if (!placa.matches("[A-Z]{3}\\d[A-Z0-9]\\d{2}")) {
            throw new IllegalArgumentException("placa invalida: " + placa);
        }
        return placa;
    }

    protected static int ano(JsonObject requisicao) {
        int ano = inteiro(requisicao, "ano");
        if (ano < 1900 || ano > Year.now().getValue()) {
            throw new IllegalArgumentException("ano invalido: " + ano);
        }
        return ano;
    }

    protected static String dinheiro(double valor) {
        return NumberFormat.getCurrencyInstance(new Locale("pt", "BR")).format(valor);
    }
}
