package detran;

public class Veiculo {

    String placa;
    String modelo;
    double valor;
    String cpfProprietario;
    int anoEmplacamento;
    String detran; // UF de quem emplacou

    Veiculo(String placa, String modelo, double valor, String cpfProprietario, int anoEmplacamento, String detran) {
        this.placa = placa;
        this.modelo = modelo;
        this.valor = valor;
        this.cpfProprietario = cpfProprietario;
        this.anoEmplacamento = anoEmplacamento;
        this.detran = detran;
    }
}
