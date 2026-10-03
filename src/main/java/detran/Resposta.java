package detran;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class Resposta {

    String idRequisicao;
    boolean sucesso;
    String mensagem;
    List<Map<String, Object>> dados = new ArrayList<>();

    static Resposta ok(String mensagem) {
        Resposta r = new Resposta();
        r.sucesso = true;
        r.mensagem = mensagem;
        return r;
    }

    static Resposta erro(String mensagem) {
        Resposta r = new Resposta();
        r.sucesso = false;
        r.mensagem = mensagem;
        return r;
    }

    Resposta com(Map<String, Object> item) {
        dados.add(item);
        return this;
    }

    static Map<String, Object> item(Object... camposEValores) {
        Map<String, Object> item = new LinkedHashMap<>();
        for (int i = 0; i < camposEValores.length; i += 2) {
            item.put(String.valueOf(camposEValores[i]), camposEValores[i + 1]);
        }
        return item;
    }
}
