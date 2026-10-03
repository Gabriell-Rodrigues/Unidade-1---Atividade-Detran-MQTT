package detran;

import com.google.gson.JsonObject;

import java.util.Scanner;

public class Terminal {

    public static void main(String[] args) throws Exception {
        Cliente cliente = new Cliente(Mqtt.variavel("UF", "SE"));
        Scanner entrada = new Scanner(System.in);

        while (true) {
            System.out.println();
            System.out.println("DENATRAN - terminal do DETRAN-" + cliente.getUf());
            System.out.println(" 1. Emplacar veiculo");
            System.out.println(" 2. Calcular IPVA do veiculo");
            System.out.println(" 3. Transferir proprietario de um veiculo");
            System.out.println(" 4. Cadastrar condutor");
            System.out.println(" 5. Lancar multa");
            System.out.println(" 6. Veiculos emplacados em um ano");
            System.out.println(" 7. Multas de um veiculo");
            System.out.println(" 8. Multas de um condutor em um ano");
            System.out.println(" 9. Multas lancadas em um ano");
            System.out.println("10. Os 5 condutores com mais pontos");
            System.out.println(" 0. Sair");
            System.out.print("Opcao: ");
            if (!entrada.hasNextLine()) {
                break;
            }
            String opcao = entrada.nextLine().trim();

            JsonObject requisicao = new JsonObject();
            String topico;
            switch (opcao) {
                case "1" -> {
                    topico = Mqtt.VEICULOS + "emplacar";
                    ler(entrada, requisicao, "placa", "Placa");
                    ler(entrada, requisicao, "modelo", "Modelo");
                    ler(entrada, requisicao, "valor", "Valor (R$)");
                    ler(entrada, requisicao, "cpf", "CPF do condutor");
                }
                case "2" -> {
                    topico = Mqtt.VEICULOS + "ipva";
                    ler(entrada, requisicao, "placa", "Placa");
                }
                case "3" -> {
                    topico = Mqtt.CONDUTORES + "transferir";
                    ler(entrada, requisicao, "placa", "Placa do veiculo");
                    ler(entrada, requisicao, "cpf", "CPF do novo dono");
                }
                case "4" -> {
                    topico = Mqtt.CONDUTORES + "cadastrar";
                    ler(entrada, requisicao, "cpf", "CPF");
                    ler(entrada, requisicao, "nome", "Nome");
                }
                case "5" -> {
                    topico = Mqtt.MULTAS + "lancar";
                    ler(entrada, requisicao, "ano", "Ano");
                    ler(entrada, requisicao, "descricao", "Descricao");
                    ler(entrada, requisicao, "pontuacao", "Pontuacao");
                    ler(entrada, requisicao, "placa", "Placa do veiculo");
                }
                case "6" -> {
                    topico = Mqtt.VEICULOS + "emplacados-no-ano";
                    ler(entrada, requisicao, "ano", "Ano");
                }
                case "7" -> {
                    topico = Mqtt.MULTAS + "por-veiculo";
                    ler(entrada, requisicao, "placa", "Placa do veiculo");
                    ler(entrada, requisicao, "ano", "Ano (Enter para todos)");
                }
                case "8" -> {
                    topico = Mqtt.MULTAS + "por-condutor";
                    ler(entrada, requisicao, "cpf", "CPF do condutor");
                    ler(entrada, requisicao, "ano", "Ano");
                }
                case "9" -> {
                    topico = Mqtt.MULTAS + "por-ano";
                    ler(entrada, requisicao, "ano", "Ano");
                }
                case "10" -> topico = Mqtt.MULTAS + "ranking";
                case "0" -> {
                    cliente.fechar();
                    return;
                }
                default -> {
                    System.out.println("Opcao invalida");
                    continue;
                }
            }
            Cliente.imprimir(cliente.requisitar(topico, requisicao));
        }
        cliente.fechar();
    }

    private static void ler(Scanner entrada, JsonObject requisicao, String campo, String rotulo) {
        System.out.print(rotulo + ": ");
        requisicao.addProperty(campo, entrada.hasNextLine() ? entrada.nextLine().trim() : "");
    }
}
