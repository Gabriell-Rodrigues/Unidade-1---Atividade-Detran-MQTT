package detran;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.time.Year;

public class Teste {

    static Cliente cliente;
    static int falhas = 0;

    public static void main(String[] args) throws Exception {
        cliente = new Cliente("SE");
        int anoAtual = Year.now().getValue();

        System.out.println("\n>> Cadastro de condutores");
        String[][] condutores = {
                {"08231546710", "Mariana Costa"}, {"70455218903", "Joao Pedro Almeida"}, {"51920837461", "Tatiane Farias"},
                {"32678904152", "Ricardo Menezes"}, {"96014372285", "Luana Batista"}, {"14589063327", "Carlos Eduardo Nunes"}};
        for (String[] c : condutores) {
            JsonObject r = enviar(Mqtt.CONDUTORES + "cadastrar", "cpf", c[0], "nome", c[1]);
            if (!r.get("sucesso").getAsBoolean() && r.get("mensagem").getAsString().contains("ja existe")) {
                System.out.println("Os servicos ja tem dados. Rode 'docker compose down -v' e suba de novo antes do teste.");
                System.exit(1);
            }
            conferir(r.get("sucesso").getAsBoolean(), "cadastrar " + c[1]);
        }
        conferir(!sucesso(enviar(Mqtt.CONDUTORES + "cadastrar", "cpf", "082.315.467-10", "nome", "Mariana C.")),
                "nao aceitar CPF repetido");

        System.out.println("\n>> Emplacamento");
        String[][] veiculos = {
                {"ABC1D23", "Gol", "45000", "08231546710"}, {"BRA2E19", "Onix", "78000", "70455218903"},
                {"SER1A11", "HB20", "65000", "51920837461"}, {"QWE4567", "Corolla", "150000", "32678904152"},
                {"MNO7P89", "Kwid", "55000", "96014372285"}, {"XYZ9K88", "Strada", "98000", "14589063327"}};
        for (String[] v : veiculos) {
            conferir(sucesso(enviar(Mqtt.VEICULOS + "emplacar", "placa", v[0], "modelo", v[1], "valor", v[2], "cpf", v[3])),
                    "emplacar " + v[0]);
        }
        conferir(!sucesso(enviar(Mqtt.VEICULOS + "emplacar", "placa", "ABC1D23", "modelo", "Uno", "valor", "30000", "cpf", "70455218903")),
                "nao aceitar placa repetida");
        conferir(!sucesso(enviar(Mqtt.VEICULOS + "emplacar", "placa", "JKL1M23", "modelo", "Uno", "valor", "30000", "cpf", "12312312399")),
                "nao emplacar para CPF nao cadastrado");

        System.out.println("\n>> IPVA (aliquota de 2%)");
        conferir(mensagem(enviar(Mqtt.VEICULOS + "ipva", "placa", "ABC1D23")).contains("900,00"), "IPVA de R$ 45.000,00 = R$ 900,00");
        conferir(mensagem(enviar(Mqtt.VEICULOS + "ipva", "placa", "QWE4567")).contains("3.000,00"), "IPVA de R$ 150.000,00 = R$ 3.000,00");

        System.out.println("\n>> Multas antes da transferencia");
        lancar("2024", "Estacionar em local proibido", "4", "ABC1D23");
        lancar("2025", "Excesso de velocidade ate 20%", "4", "ABC1D23");
        lancar("2025", "Avancar sinal vermelho", "7", "BRA2E19");
        lancar("2025", "Usar celular ao dirigir", "7", "BRA2E19");
        lancar("2025", "Excesso de velocidade acima de 50%", "7", "SER1A11");
        lancar("2026", "Nao usar cinto de seguranca", "5", "QWE4567");
        lancar("2026", "Estacionar em fila dupla", "5", "MNO7P89");
        lancar("2026", "Farol apagado na rodovia", "4", "XYZ9K88");
        conferir(!sucesso(enviar(Mqtt.MULTAS + "lancar", "ano", "2025", "descricao", "Teste", "pontuacao", "3", "placa", "ZZZ9Z99")),
                "nao lancar multa para veiculo inexistente");

        System.out.println("\n>> Transferencia do ABC1D23 de Mariana para Ricardo");
        conferir(sucesso(enviar(Mqtt.CONDUTORES + "transferir", "placa", "ABC1D23", "cpf", "32678904152")), "transferir ABC1D23");
        conferir(!sucesso(enviar(Mqtt.CONDUTORES + "transferir", "placa", "ABC1D23", "cpf", "32678904152")),
                "nao transferir para quem ja e dono");
        lancar("2026", "Excesso de velocidade ate 20%", "4", "ABC1D23");

        System.out.println("\n>> Consultas");
        conferir(quantidade(enviar(Mqtt.VEICULOS + "emplacados-no-ano", "ano", String.valueOf(anoAtual))) == 6,
                "6 veiculos emplacados em " + anoAtual);
        conferir(quantidade(enviar(Mqtt.VEICULOS + "emplacados-no-ano", "ano", String.valueOf(anoAtual - 1))) == 0,
                "nenhum veiculo emplacado em " + (anoAtual - 1));

        JsonObject multasGol = enviar(Mqtt.MULTAS + "por-veiculo", "placa", "ABC1D23", "ano", "");
        conferir(quantidade(multasGol) == 3, "3 multas do ABC1D23 em todos os anos");
        conferir(condutor(multasGol, 0).equals("Mariana Costa") && condutor(multasGol, 2).equals("Ricardo Menezes"),
                "multas antigas com Mariana e a nova com Ricardo");
        conferir(quantidade(enviar(Mqtt.MULTAS + "por-veiculo", "placa", "ABC1D23", "ano", "2025")) == 1,
                "1 multa do ABC1D23 em 2025");

        conferir(quantidade(enviar(Mqtt.MULTAS + "por-condutor", "cpf", "70455218903", "ano", "2025")) == 2,
                "2 multas de Joao Pedro em 2025");
        conferir(quantidade(enviar(Mqtt.MULTAS + "por-condutor", "cpf", "32678904152", "ano", "2026")) == 2,
                "2 multas de Ricardo em 2026");

        conferir(quantidade(enviar(Mqtt.MULTAS + "por-ano", "ano", "2024")) == 1, "1 multa lancada em 2024");
        conferir(quantidade(enviar(Mqtt.MULTAS + "por-ano", "ano", "2025")) == 4, "4 multas lancadas em 2025");
        conferir(quantidade(enviar(Mqtt.MULTAS + "por-ano", "ano", "2026")) == 4, "4 multas lancadas em 2026");

        JsonObject ranking = enviar(Mqtt.MULTAS + "ranking");
        String[] esperado = {"Joao Pedro Almeida", "Ricardo Menezes", "Mariana Costa", "Tatiane Farias", "Luana Batista"};
        boolean ordemCerta = quantidade(ranking) == 5;
        for (int i = 0; ordemCerta && i < esperado.length; i++) {
            ordemCerta = condutor(ranking, i).equals(esperado[i]);
        }
        conferir(ordemCerta, "ranking: Joao Pedro (14), Ricardo (9), Mariana (8), Tatiane (7), Luana (5)");

        System.out.println();
        System.out.println(falhas == 0 ? "Todas as verificacoes passaram" : falhas + " verificacao(oes) falharam");
        cliente.fechar();
        System.exit(falhas == 0 ? 0 : 1);
    }

    static JsonObject enviar(String topico, String... camposEValores) throws Exception {
        JsonObject requisicao = new JsonObject();
        for (int i = 0; i < camposEValores.length; i += 2) {
            requisicao.addProperty(camposEValores[i], camposEValores[i + 1]);
        }
        System.out.println(topico + " " + requisicao);
        JsonObject resposta = cliente.requisitar(topico, requisicao);
        Cliente.imprimir(resposta);
        return resposta;
    }

    static void lancar(String ano, String descricao, String pontos, String placa) throws Exception {
        conferir(sucesso(enviar(Mqtt.MULTAS + "lancar", "ano", ano, "descricao", descricao, "pontuacao", pontos, "placa", placa)),
                "lancar multa " + placa + " " + ano);
    }

    static void conferir(boolean condicao, String descricao) {
        System.out.println((condicao ? "  [OK] " : "  [FALHOU] ") + descricao);
        if (!condicao) {
            falhas++;
        }
    }

    static boolean sucesso(JsonObject resposta) {
        return resposta.get("sucesso").getAsBoolean();
    }

    static String mensagem(JsonObject resposta) {
        return resposta.get("mensagem").getAsString();
    }

    static int quantidade(JsonObject resposta) {
        return resposta.has("dados") ? resposta.getAsJsonArray("dados").size() : -1;
    }

    static String condutor(JsonObject resposta, int linha) {
        JsonArray dados = resposta.getAsJsonArray("dados");
        return dados.get(linha).getAsJsonObject().get("condutor").getAsString();
    }
}
