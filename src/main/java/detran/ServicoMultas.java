package detran;

import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ServicoMultas extends Microsservico {

    static class Dados {
        List<Multa> multas = new ArrayList<>();
        // cópias mantidas pelos eventos dos outros serviços
        Map<String, String> proprietarios = new TreeMap<>(); // placa -> CPF
        Map<String, String> nomes = new TreeMap<>();          // CPF -> nome
    }

    private final Dados dados;

    ServicoMultas() {
        super("multas");
        dados = carregar(Dados.class, new Dados());
    }

    public static void main(String[] args) throws Exception {
        new ServicoMultas().iniciar(
                Mqtt.MULTAS + "+",      // denatran/multas/+
                Mqtt.EVENTOS + "#");    // denatran/eventos/#
    }

    @Override
    protected Resposta atender(String operacao, JsonObject requisicao) {
        return switch (operacao) {
            case "lancar" -> lancar(requisicao);
            case "por-veiculo" -> porVeiculo(requisicao);
            case "por-condutor" -> porCondutor(requisicao);
            case "por-ano" -> porAno(requisicao);
            case "ranking" -> ranking();
            default -> throw new IllegalArgumentException("operacao desconhecida: " + operacao);
        };
    }

    private Resposta lancar(JsonObject requisicao) {
        int ano = ano(requisicao);
        String descricao = texto(requisicao, "descricao");
        int pontuacao = inteiro(requisicao, "pontuacao");
        String placa = placa(requisicao);

        if (pontuacao <= 0) {
            throw new IllegalArgumentException("pontuacao deve ser maior que zero");
        }
        String cpf = dados.proprietarios.get(placa);
        if (cpf == null) {
            throw new IllegalArgumentException("veiculo " + placa + " nao encontrado");
        }

        // a multa fica com o dono do veículo no momento do lançamento
        Multa multa = new Multa(ano, descricao, pontuacao, placa, cpf);
        dados.multas.add(multa);
        salvar(dados);
        return Resposta.ok("multa lancada para o veiculo " + placa + " (condutor " + nome(cpf) + ")").com(linha(multa));
    }

    private Resposta porVeiculo(JsonObject requisicao) {
        String placa = placa(requisicao);
        boolean filtraAno = requisicao.has("ano") && !requisicao.get("ano").getAsString().isBlank();
        int ano = filtraAno ? ano(requisicao) : 0;
        if (!dados.proprietarios.containsKey(placa)) {
            throw new IllegalArgumentException("veiculo " + placa + " nao encontrado");
        }

        Resposta resposta = Resposta.ok("");
        for (Multa multa : dados.multas) {
            if (multa.placa.equals(placa) && (!filtraAno || multa.ano == ano)) {
                resposta.com(linha(multa));
            }
        }
        resposta.mensagem = resposta.dados.size() + " multa(s) do veiculo " + placa + (filtraAno ? " em " + ano : "");
        return resposta;
    }

    private Resposta porCondutor(JsonObject requisicao) {
        String cpf = cpf(requisicao, "cpf");
        int ano = ano(requisicao);
        if (!dados.nomes.containsKey(cpf)) {
            throw new IllegalArgumentException("condutor de CPF " + cpf + " nao esta cadastrado");
        }

        Resposta resposta = Resposta.ok("");
        int pontos = 0;
        for (Multa multa : dados.multas) {
            if (multa.cpfCondutor.equals(cpf) && multa.ano == ano) {
                resposta.com(linha(multa));
                pontos += multa.pontuacao;
            }
        }
        resposta.mensagem = resposta.dados.size() + " multa(s) de " + nome(cpf) + " em " + ano + ", total de " + pontos + " pontos";
        return resposta;
    }

    private Resposta porAno(JsonObject requisicao) {
        int ano = ano(requisicao);
        Resposta resposta = Resposta.ok("");
        for (Multa multa : dados.multas) {
            if (multa.ano == ano) {
                resposta.com(linha(multa));
            }
        }
        resposta.mensagem = resposta.dados.size() + " multa(s) lancada(s) em " + ano;
        return resposta;
    }

    private Resposta ranking() {
        Map<String, Integer> pontosPorCpf = new HashMap<>();
        for (Multa multa : dados.multas) {
            pontosPorCpf.merge(multa.cpfCondutor, multa.pontuacao, Integer::sum);
        }

        List<Map.Entry<String, Integer>> ordenado = new ArrayList<>(pontosPorCpf.entrySet());
        ordenado.sort((a, b) -> !a.getValue().equals(b.getValue())
                ? b.getValue() - a.getValue()
                : nome(a.getKey()).compareTo(nome(b.getKey())));

        Resposta resposta = Resposta.ok("condutores com mais pontos em multas (ate 5)");
        for (int i = 0; i < ordenado.size() && i < 5; i++) {
            String cpf = ordenado.get(i).getKey();
            resposta.com(Resposta.item("posicao", i + 1, "condutor", nome(cpf), "cpf", cpf, "pontos", ordenado.get(i).getValue()));
        }
        return resposta;
    }

    @Override
    protected void aoReceberEvento(String evento, JsonObject conteudo) {
        switch (evento) {
            case "condutor-cadastrado" -> dados.nomes.put(conteudo.get("cpf").getAsString(), conteudo.get("nome").getAsString());
            case "veiculo-emplacado" -> dados.proprietarios.put(conteudo.get("placa").getAsString(), conteudo.get("cpf").getAsString());
            case "veiculo-transferido" -> dados.proprietarios.put(conteudo.get("placa").getAsString(), conteudo.get("cpfNovo").getAsString());
            default -> {
                return;
            }
        }
        salvar(dados);
    }

    private String nome(String cpf) {
        return dados.nomes.getOrDefault(cpf, cpf);
    }

    private Map<String, Object> linha(Multa multa) {
        return Resposta.item("ano", multa.ano, "descricao", multa.descricao, "pontos", multa.pontuacao,
                "placa", multa.placa, "condutor", nome(multa.cpfCondutor), "cpf", multa.cpfCondutor);
    }
}
