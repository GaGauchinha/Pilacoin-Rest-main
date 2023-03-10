package br.ufsm.politecnico.csi.tapw.pila.servidor.service;

import br.ufsm.politecnico.csi.tapw.pila.controller.MineracaoController;
import br.ufsm.politecnico.csi.tapw.pila.model.PilacoinModel;
import br.ufsm.politecnico.csi.tapw.pila.servidor.repository.PilaRepository;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.tomcat.util.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.Date;

import static java.math.BigInteger.valueOf;

@Service
public class MineracaoService{

    @Autowired
    private PilaRepository pilaRepository;
    @Autowired(required = false)
    private PilacoinModel pilacoinModel;
    @Autowired
    private PilaService pilaService;
    @Autowired
    private MineracaoController mineracaoController = new MineracaoController();

    public static KeyPair keyPair;

    @Value("${endereco.server}")
    private static String enderecoServer;

    @SneakyThrows
    public void initPilacoin (boolean minerar) {
       UsuarioService UsuarioService = new UsuarioService();
        KeyPair keyPair =UsuarioService.leKeyPair();
        BigInteger dificuldade = mineracaoController.getDificuldade();

        BigInteger numTentativas = valueOf(0);

            dificuldade = WebsocketService.sessionHandler.getDificuldade();
            PublicKey publicKey = UsuarioService.getPublicKey();

            if(dificuldade != null){

                SecureRandom sr = new SecureRandom();
                BigInteger mNumber = new BigInteger(128, sr);


                PilacoinModel pilaCoin = new PilacoinModel();
                pilaCoin.setDataCriacao(new Date());
                pilaCoin.setChaveCriador(publicKey.getEncoded());
                pilaCoin.setNonce(new BigInteger(128, sr).abs());
                pilaCoin.setIdCriador("Gabi");
                pilaCoin.setStatus(PilacoinModel.VALIDACAO);

                ObjectMapper objectMapper = new ObjectMapper();
                objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);


                String pilaJson = objectMapper.writeValueAsString(pilaCoin);


                MessageDigest md = MessageDigest.getInstance("SHA-256");

                byte[] hash = md.digest(pilaJson.getBytes("UTF-8"));

                BigInteger numHash = new BigInteger(hash).abs();


                if (numHash.compareTo(dificuldade) < 0) {
                    System.out.println(Base64.encodeBase64String(keyPair.getPublic().getEncoded()));
                    System.out.println("Minerou");
                    System.out.println("Numhash:" +numHash);
                    System.out.println("Dificuldade:" +dificuldade);

                    registrarPila(pilaJson , pilaCoin.getNonce());

                } else {
                    numTentativas =  numTentativas.add(BigInteger.ONE);
                }

            }


        }

//
//    @SneakyThrows
//    private void Minerador (byte[] hash, PilacoinModel pilacoinModel){
//
//        BigInteger numHash = new BigInteger(hash).abs();
//        BigInteger dificuldade = mineracaoController.getDificuldade();
//        SecureRandom rnd = new SecureRandom();
//        BigInteger nonce = pilacoinModel.getNonce();
//        BigInteger numTentativas = valueOf(0);
//
//        if (numHash.compareTo(dificuldade) < 0) {
//            System.out.println(Base64.encodeBase64String(keyPair.
//                    getPublic().getEncoded()));
//
//            System.out.println("Minerou");
//            System.out.printf("NumHash:" + numHash);
//            System.out.printf("Dificuldade:" + dificuldade);
//            System.out.println(" nonce: " + pilacoinModel.getNonce());
//            String pilaJson = new ObjectMapper().writeValueAsString(pilacoinModel);
//
//            System.out.println(pilaJson);
//
//            MessageDigest md = null;
//            md = MessageDigest.getInstance("SHA-256");
//            md.digest(pilaJson.getBytes("UTF-8"));
//
//            registrarPila(pilaJson, pilacoinModel.getNonce());
//
//        } else {
//            numTentativas =  numTentativas.add(BigInteger.ONE);
//
//        }
//    }

    @SneakyThrows
    private void registrarPila(String pilaJson, BigInteger nonce){
        byte[] publicKeyBytes = Files.readAllBytes(Path.of("pub.key"));

        RestTemplate restTemplate = new RestTemplate();
        ResponseEntity<PilacoinModel> resp = null;


        try {
            //posta meu pila
            RequestEntity<String> requestEntity = RequestEntity.post(new URL(
                            "http://"+ "srv-ceesp.proj.ufsm.br:8097" +
                                    "/pilacoin/").toURI())
                    .contentType(MediaType.APPLICATION_JSON).body(pilaJson);

            resp = restTemplate.exchange(requestEntity, PilacoinModel.class);
            if (resp.getStatusCode() == HttpStatus.OK){
                System.out.println("ENVIOU PRO SERVER");
                validarPila(String.valueOf(nonce), resp.getBody());
            }
        } catch (Exception e) {
            System.out.println("Erro ao registrar pila: "+e.getMessage());
        }
}


    @SneakyThrows
    public void validarPila(String nonce, PilacoinModel pilacoinModel){
        ResponseEntity<String> resp = null;
        RestTemplate restTemplate = new RestTemplate();
        try {
            //get nos meus pilas
            resp= restTemplate.getForEntity("http://"+
                    "srv-ceesp.proj.ufsm.br:8097" + "/pilacoin/?nonce="+
                    nonce, String.class);

            if (resp.getStatusCode() == HttpStatus.OK){
                System.out.println("Pila validado com sucesso!!! (???'???'???)");
                File arquivo;
                FileWriter escritorArquivo;
                BufferedWriter escritorBuffer;

                try {

                    this.pilaRepository.save(pilacoinModel);

                    arquivo = new File("meus_pilas.txt");
                    //gera o log do meu pila
                    escritorArquivo = new FileWriter(arquivo, true);
                    escritorBuffer = new BufferedWriter(escritorArquivo);

                    escritorBuffer.write(resp.getBody().toString() + "\n");
                    escritorBuffer.newLine();

                    escritorBuffer.flush();
                    escritorArquivo.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        catch (Exception e) {
            System.out.println("Pila invalido: "+e.getMessage());
        }
    }
}
