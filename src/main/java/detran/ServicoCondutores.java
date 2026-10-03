package detran;

import com.google.gson.JsonObject;

import java.util.Map;
import java.util.TreeMap;

public class ServicoCondutores extends Microsservico {

    static class Dados {
        Map<String, Condutor> condutores = new TreeMap<>();
        Map<String, String> proprietarios = new TreeMap<>(); // placa -> CPF, pelos eventos de emplacamento
    }

    private final Dados dados;

    ServicoCondutores() {
        super("condutores");
        dados = carregar(Dados.class, new Dados());
    }

    public static void main(String[] args) throws Exception {
        new ServicoCondutores().iniciar(
                Mqtt.CONDUTORES + "+",
                Mqtt.EVENTOS + "veiculo-emplacado");
    }

    @Override
    protected Resposta atender(String operacao, JsonObject requisicao) {
        return switch (operacao) {
            case "cadastrar" -> cadastrar(requisicao);
            case "transferir" -> transferir(requisicao);
            default -> throw new IllegalArgumentException("operacao desconhecida: " + operacao);
        };
    }

    private Resposta cadastrar(JsonObject requisicao) {
        String cpf = cpf(requisicao, "cpf");
        String nome = texto(requisicao, "nome");
        if (dados.condutores.containsKey(cpf)) {
            throw new IllegalArgumentException("ja existe condutor com CPF " + cpf);
        }

        dados.condutores.put(cpf, new Condutor(cpf, nome));
        salvar(dados);

        JsonObject evento = new JsonObject();
        evento.addProperty("cpf", cpf);
        evento.addProperty("nome", nome);
        publicarEvento("condutor-cadastrado", evento);

        return Resposta.ok("condutor " + nome + " cadastrado").com(Resposta.item("cpf", cpf, "nome", nome));
    }

    private Resposta transferir(JsonObject requisicao) {
        String placa = placa(requisicao);
        String cpfNovo = cpf(requisicao, "cpf");

        String cpfAnterior = dados.proprietarios.get(placa);
        if (cpfAnterior == null) {
            throw new IllegalArgumentException("veiculo " + placa + " nao encontrado");
        }
        Condutor novo = dados.condutores.get(cpfNovo);
        if (novo == null) {
            throw new IllegalArgumentException("condutor de CPF " + cpfNovo + " nao esta cadastrado");
        }
        if (cpfAnterior.equals(cpfNovo)) {
            throw new IllegalArgumentException("o veiculo " + placa + " ja pertence a " + novo.nome);
        }

        dados.proprietarios.put(placa, cpfNovo);
        salvar(dados);

        JsonObject evento = new JsonObject();
        evento.addProperty("placa", placa);
        evento.addProperty("cpfAnterior", cpfAnterior);
        evento.addProperty("cpfNovo", cpfNovo);
        publicarEvento("veiculo-transferido", evento);

        Condutor anterior = dados.condutores.get(cpfAnterior);
        String nomeAnterior = anterior != null ? anterior.nome : cpfAnterior;
        return Resposta.ok("veiculo " + placa + " transferido de " + nomeAnterior + " para " + novo.nome)
                .com(Resposta.item("placa", placa, "proprietario anterior", nomeAnterior + " (" + cpfAnterior + ")",
                        "novo proprietario", novo.nome + " (" + cpfNovo + ")"));
    }

    @Override
    protected void aoReceberEvento(String evento, JsonObject conteudo) {
        if (evento.equals("veiculo-emplacado")) {
            dados.proprietarios.put(conteudo.get("placa").getAsString(), conteudo.get("cpf").getAsString());
            salvar(dados);
        }
    }
}
