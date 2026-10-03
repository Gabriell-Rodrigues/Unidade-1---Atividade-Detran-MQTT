package detran;

public class Multa {

    int ano;
    String descricao;
    int pontuacao;
    String placa;
    String cpfCondutor;

    Multa(int ano, String descricao, int pontuacao, String placa, String cpfCondutor) {
        this.ano = ano;
        this.descricao = descricao;
        this.pontuacao = pontuacao;
        this.placa = placa;
        this.cpfCondutor = cpfCondutor;
    }
}
