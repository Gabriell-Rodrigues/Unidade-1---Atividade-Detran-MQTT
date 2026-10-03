package detran;

import com.google.gson.JsonObject;

import java.time.Year;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class ServicoVeiculos extends Microsservico {

    static final double ALIQUOTA_IPVA = 0.02;

    static class Dados {
        Map<String, Veiculo> veiculos = new TreeMap<>();
        Set<String> condutores = new TreeSet<>(); // CPFs recebidos pelos eventos de cadastro
    }

    private final Dados dados;

    ServicoVeiculos() {
        super("veiculos");
        dados = carregar(Dados.class, new Dados());
    }

    public static void main(String[] args) throws Exception {
        new ServicoVeiculos().iniciar(
                Mqtt.VEICULOS + "+",
                Mqtt.EVENTOS + "condutor-cadastrado",
                Mqtt.EVENTOS + "veiculo-transferido");
    }

    @Override
    protected Resposta atender(String operacao, JsonObject requisicao) {
        return switch (operacao) {
            case "emplacar" -> emplacar(requisicao);
            case "ipva" -> calcularIpva(requisicao);
            case "emplacados-no-ano" -> emplacadosNoAno(requisicao);
            default -> throw new IllegalArgumentException("operacao desconhecida: " + operacao);
        };
    }

    private Resposta emplacar(JsonObject requisicao) {
        String placa = placa(requisicao);
        String modelo = texto(requisicao, "modelo");
        double valor = numero(requisicao, "valor");
        String cpf = cpf(requisicao, "cpf");
        String detran = requisicao.has("detran") ? requisicao.get("detran").getAsString() : "-";

        if (dados.veiculos.containsKey(placa)) {
            throw new IllegalArgumentException("a placa " + placa + " ja esta emplacada");
        }
        if (valor <= 0) {
            throw new IllegalArgumentException("valor do veiculo deve ser maior que zero");
        }
        if (!dados.condutores.contains(cpf)) {
            throw new IllegalArgumentException("condutor de CPF " + cpf + " nao esta cadastrado");
        }

        Veiculo veiculo = new Veiculo(placa, modelo, valor, cpf, Year.now().getValue(), detran);
        dados.veiculos.put(placa, veiculo);
        salvar(dados);

        JsonObject evento = new JsonObject();
        evento.addProperty("placa", placa);
        evento.addProperty("modelo", modelo);
        evento.addProperty("cpf", cpf);
        publicarEvento("veiculo-emplacado", evento);

        return Resposta.ok("veiculo " + placa + " emplacado em " + veiculo.anoEmplacamento).com(resumo(veiculo));
    }

    private Resposta calcularIpva(JsonObject requisicao) {
        Veiculo veiculo = buscar(placa(requisicao));
        double ipva = veiculo.valor * ALIQUOTA_IPVA;
        return Resposta.ok("IPVA do veiculo " + veiculo.placa + ": " + dinheiro(ipva))
                .com(Resposta.item("placa", veiculo.placa, "modelo", veiculo.modelo,
                        "valor", dinheiro(veiculo.valor), "aliquota", "2%", "ipva", dinheiro(ipva)));
    }

    private Resposta emplacadosNoAno(JsonObject requisicao) {
        int ano = ano(requisicao);
        Resposta resposta = Resposta.ok("");
        for (Veiculo veiculo : dados.veiculos.values()) {
            if (veiculo.anoEmplacamento == ano) {
                resposta.com(resumo(veiculo));
            }
        }
        resposta.mensagem = resposta.dados.size() + " veiculo(s) emplacado(s) em " + ano;
        return resposta;
    }

    @Override
    protected void aoReceberEvento(String evento, JsonObject conteudo) {
        switch (evento) {
            case "condutor-cadastrado" -> dados.condutores.add(conteudo.get("cpf").getAsString());
            case "veiculo-transferido" -> {
                Veiculo veiculo = dados.veiculos.get(conteudo.get("placa").getAsString());
                if (veiculo != null) {
                    veiculo.cpfProprietario = conteudo.get("cpfNovo").getAsString();
                    System.out.println("[" + nome + "] " + veiculo.placa + " agora pertence a " + veiculo.cpfProprietario);
                }
            }
            default -> {
                return;
            }
        }
        salvar(dados);
    }

    private Veiculo buscar(String placa) {
        Veiculo veiculo = dados.veiculos.get(placa);
        if (veiculo == null) {
            throw new IllegalArgumentException("veiculo " + placa + " nao encontrado");
        }
        return veiculo;
    }

    private Map<String, Object> resumo(Veiculo veiculo) {
        return Resposta.item("placa", veiculo.placa, "modelo", veiculo.modelo, "valor", dinheiro(veiculo.valor),
                "cpf proprietario", veiculo.cpfProprietario, "ano", veiculo.anoEmplacamento, "detran", veiculo.detran);
    }
}
