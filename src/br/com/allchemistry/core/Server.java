/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package br.com.allchemistry.core;

import br.com.allchemistry.spf.SPF;
import br.com.allchemistry.spf.SPF.Distribution;
import br.com.allchemistry.spf.SPF.Status;
import br.com.allchemistry.whois.AutonomousSystem;
import br.com.allchemistry.whois.Domain;
import br.com.allchemistry.whois.Handle;
import br.com.allchemistry.whois.NameServer;
import br.com.allchemistry.whois.Owner;
import br.com.allchemistry.whois.Subnet;
import br.com.allchemistry.whois.SubnetIPv4;
import br.com.allchemistry.whois.SubnetIPv6;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.Semaphore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.naming.directory.InitialDirContext;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.net.whois.WhoisClient;

/**
 * Representa um modelo de servidor com métodos comuns.
 * 
 * @author Leandro Carlos Rodrigues <leandro@allchemistry.com.br>
 */
public abstract class Server extends Thread {
    
    /**
     * Varivável que determina se o serviço deve continuar rodando.
     */
    private boolean run = true;
    
    /**
     * Armazena todos os servidores intanciados.
     */
    private static final LinkedList<Server> SERVER_LIST = new LinkedList<Server>();
    
    /**
     * Instancia um servidor.
     * @param name nome do servidor e da thread.
     */
    protected Server(String name) {
        super(name);
        // Todo servidor recebe prioridade máxima.
        setPriority(Thread.MAX_PRIORITY);
        // Adiciona novo servidor na lista.
        SERVER_LIST.add(this);
    }
    
    protected boolean continueListenning() {
        return run;
    }
    
    /**
     * Carregamento de cache em disco.
     */
    public static void loadCache() {
        Owner.load();
        Domain.load();
        AutonomousSystem.load();
        SubnetIPv4.load();
        SubnetIPv6.load();
        Handle.load();
        NameServer.load();
        SPF.load();
   }
    
    /**
     * Armazenamento de cache em disco.
     */
    protected static void storeCache() {
        Owner.store();
        Domain.store();
        AutonomousSystem.store();
        SubnetIPv4.store();
        SubnetIPv6.store();
        Handle.store();
        NameServer.store();
        SPF.store();
    }
    
    private static SecretKey privateKey = null;
    
    private static SecretKey getPrivateKey() {
        if (privateKey == null) {
            try {
                File file = new File("server.key");
                if (file.exists()) {
                    FileInputStream fileInputStream = new FileInputStream(file);
                    try {
                        privateKey = SerializationUtils.deserialize(fileInputStream);
                    } finally {
                        fileInputStream.close();
                    }
                } else {
                    KeyGenerator keyGen = KeyGenerator.getInstance("AES");
                    keyGen.init(new SecureRandom());
                    SecretKey key = keyGen.generateKey();
                    FileOutputStream outputStream = new FileOutputStream(file);
                    try {
                        SerializationUtils.serialize(key, outputStream);
                    } finally {
                        outputStream.close();
                    }
                    privateKey = key;
                }
            } catch (Exception ex) {
                Server.logError(ex);
            }
        }
        return privateKey;
    }
    
    public static String encrypt(String message) throws ProcessException {
        if (message == null) {
            return null;
        } else {
            try {
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.ENCRYPT_MODE, getPrivateKey());
                byte[] code = cipher.doFinal(message.getBytes("UTF8"));
                return new String(Base64Coder.encode(code));
            } catch (Exception ex) {
                throw new ProcessException("ERROR: ENCRYPTION", ex);
            }
        }
    }
    
    public static String decrypt(String code) throws ProcessException {
        if (code == null) {
            return null;
        } else {
            try {
                Cipher cipher = Cipher.getInstance("AES");
                cipher.init(Cipher.DECRYPT_MODE, getPrivateKey());
                byte[] message = cipher.doFinal(Base64Coder.decode(code));
                return new String(message, "UTF8");
            } catch (Exception ex) {
                throw new ProcessException("ERROR: DECRYPTION", ex);
            }
        }
    }
    
    /**
     * Constante de formatação da data no log.
     * Padrão ISO 8601
     * 
     * Um objeto SimpleDateFormat não é thread safety,
     * portanto é necessário utilizar sincronismo
     * nos métodos que o utilizam.
     */
    private static final SimpleDateFormat FORMAT_DATE_LOG = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
    
    /**
     * Constante que representa a quantidade de tempo de um dia em milisegundos.
     */
    public static final int DAY_TIME = 1000 * 60 * 60 * 24;
    
    private static synchronized void log(String type, String message) {
        log(type, message, null);
    }
    
    private static synchronized void log(String type, String message, Throwable ex) {
        System.out.println(
                FORMAT_DATE_LOG.format(new Date())
                + " " + type + " " + message
                );
        if (ex != null) {
            ex.printStackTrace(System.out);
        }
    }
    
    /**
     * Registra as mensagens para depuração.
     * Uma iniciativa para formalização das mensagens de log.
     * @param message a mensagem a ser registrada.
     */
    public static synchronized void logDebug(String message) {
        log("DEBUG", message);
    }
    
    /**
     * Registra os tiquetes criados.
     * Uma iniciativa para formalização das mensagens de log.
     * @param tokenSet o conjunto de tokens.
     */
    public static synchronized void logTicket(Set<String> tokenSet) {
        log("TIKET", tokenSet.toString());
    }
    
    /**
     * Registra as mensagens de erro.
     * Uma iniciativa para formalização das mensagens de log.
     * @param message a mensagem a ser registrada.
     */
    public static synchronized void logError(String message) {
        log("ERROR", message);
    }
    
    /**
     * Registra as mensagens de erro.
     * Uma iniciativa para formalização das mensagens de log.
     * @param ex a exceção a ser registrada.
     */
    public static synchronized void logError(Throwable ex) {
        if (ex != null) {
            log("ERROR", "Exception", ex);
        }
    }
    
    /**
     * Registra as consultas ao SPF do host.
     * Uma iniciativa para formalização das mensagens de log.
     * @param hostname o nome do host.
     * @param registry o registro SPF do host.
     */
    public static synchronized void logQuerySPF(
            String hostname, String registry) {
        log("SPFOK", hostname + " \"" + registry + "\"");
    }
    
    /**
     * Registra as consultas ao DNSBL do host.
     * Uma iniciativa para formalização das mensagens de log.
     * @param query a expressão da consulta.
     * @param result o resultado a ser registrado.
     */
    public static synchronized void logQueryDNSBL(
            InetAddress ipAddress, String query, String result) {
        logQuery("DNSBL", ipAddress, query, result + "\n");
    }
    
    /**
     * Registra um erro no registro SPF do host.
     * Uma iniciativa para formalização das mensagens de log.
     * @param hostname o nome do host.
     * @param registry o registro SPF do host.
     */
    public static synchronized void logErrorSPF(
            String hostname, String registry) {
        log("SPFER", hostname + " \"" + registry + "\"");
    }
    
    /**
     * Registra um ticket SPF criado.
     * Uma iniciativa para formalização das mensagens de log.
     * @param ticket o ticket SPF criado.
     */
    public static synchronized void logTicketSPF(String ticket) {
        log("SPFTK", ticket);
    }
    
    /**
     * Registra uma reclamação de spam SPF consultado.
     * Uma iniciativa para formalização das mensagens de log.
     * @param token o token SPF da mensagem original.
     */
    public static synchronized void logSpamSPF(Set<String> tokenSet) {
        log("SPFBL", tokenSet.toString());
    }
    
    /**
     * Registra um vencimento de reclamação de spam SPF consultado.
     * Uma iniciativa para formalização das mensagens de log.
     * @param token o token SPF da mensagem original.
     */
    public static synchronized void logHamSPF(Set<String> tokenSet) {
        log("SPFWL", tokenSet.toString());
    }
    
    /**
     * Registra os resultados do WHOIS.
     * Uma iniciativa para formalização das mensagens de log.
     * @param server o servidor WHOIS.
     * @param query a expressão da consulta.
     * @param result o resultado a ser registrado.
     */
    public static synchronized void logWhois(String server,
            String query, String result) {
        log("WHOIS", server + " " + query + "\n" + result);
    }
    
    private static long lastClientsFileModified = 0;
    private static final TreeMap<String,String> subnetClientsMap = new TreeMap<String,String>();
    
    /**
     * Registra as mensagens de consulta.
     * Uma iniciativa para formalização das mensagens de log.
     * @param ipAddress o IP da conexão.
     * @param time o tempo de processamento da consulta.
     * @param query a expressão da consulta.
     * @param result a expressão do resultado.
     */
    public static synchronized void logQuery(
            String type,
            InetAddress ipAddress,
//            long time,
            String query, String result) {
        File clientsFile = new File("clients.txt");
        if (!clientsFile.exists()) {
            subnetClientsMap.clear();
        } else if (clientsFile.lastModified() > lastClientsFileModified) {
            try {
                subnetClientsMap.clear();
                BufferedReader reader = new BufferedReader(new FileReader(clientsFile));
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        StringTokenizer tokenizer = new StringTokenizer(line, "\t");
                        if (tokenizer.countTokens() == 3) {
                            String cidr = tokenizer.nextToken();
                            String email = tokenizer.nextToken();
                            subnetClientsMap.put(cidr, email);
                        }
                    }
                    lastClientsFileModified = clientsFile.lastModified();
                } finally {
                    reader.close();
                }
            } catch (Exception ex) {
                Server.logError(ex);
            }
        }
        String cliente = ipAddress.getHostAddress();
        try {
            for (String cidr : subnetClientsMap.keySet()) {
                if (SubnetIPv4.isValidCIDRv4(cidr)) {
                    int mask = SubnetIPv4.getMaskNet(cidr);
                    int address1 = SubnetIPv4.getAddressNet(cidr) & mask;
                    int address2 = SubnetIPv4.getAddressIP(cliente) & mask;
                    if (address1 == address2) {
                        cliente = subnetClientsMap.get(cidr);
                        break;
                    }
                } else if (SubnetIPv6.isValidIPv6(cidr)) {
                    int index = cidr.indexOf('/');
                    short[] mask = SubnetIPv6.getMaskIPv6(cidr.substring(index));
                    short[] address1 = SubnetIPv6.split(cidr.substring(0, index), mask);
                    short[] address2 = SubnetIPv6.split(cliente, mask);
                    if (Arrays.equals(address1, address2)) {
                        cliente = subnetClientsMap.get(cidr);
                        break;
                    }
                }
            }
        } catch (Exception ex) {
            Server.logError(ex);
        }
        if (result != null) {
            result = result.replace("\n", "\\n");
        }
        System.out.println(
                FORMAT_DATE_LOG.format(new Date())
                + " " + type + " "
//                + Thread.currentThread().getName() + "(" + time + "ms) "
                + cliente + ": "
                + query + " => " + result
                );
    }
    
    /**
     * Registra as mensagens de comando.
     * Uma iniciativa para formalização das mensagens de log.
     * @param ipAddress o IP da conexão.
     * @param command a expressão do comando.
     * @param result a expressão do resultado.
     */
    public static synchronized void logCommand(InetAddress ipAddress,
            String command, String result) {
        System.out.println(
                FORMAT_DATE_LOG.format(new Date())
                + " CMMND " + ipAddress.getHostAddress() + ": "
                + command + " => " + result.replace('\n', ';')
                );
    }
    
    /**
     * Desliga todos os servidores instanciados.
     * @throws Exception se houver falha no fechamento de algum servidor.
     */
    public static void shutdown() throws Exception {
        // Inicia finalização dos servidores.
        Server.logDebug("Shutting down server...");
        for (Server server : SERVER_LIST) {
            server.run = false;
            server.close();
        }
        // Finaliza timer local.
        WHOIS_SEMAPHORE_TIMER.cancel();
        // Finaliza timer SPF.
        SPF.cancelTimer();
        // Armazena os registros em disco.
        storeCache();
    }
    
    /**
     * Finaliza servidor liberando memória e respectivos recursos.
     * @throws Exception se houver falha durante o fechamento do servidor.
     */
    protected abstract void close() throws Exception;
    
    /**
     * Timer que controla a liberação dos semáforos do WHOIS.
     */
    private static final Timer WHOIS_SEMAPHORE_TIMER = new Timer("TimerWHOIS");
    
    /**
     * Semáphoro que controla o número máximo de consultas no WHOIS.
     * Controla a taxa de 30 consultas no intervalo de 5 minutos.
     */
    private static final Semaphore WHOIS_QUERY_SEMAPHORE = new Semaphore(30);
    
    /**
     * Classe de tarefa que adquire e libera o semáforo de consulta comum do WHOIS.
     * Controla a taxa de 30 consultas no intervalo de 5 minutos.
     */
    private static class WhoisSemaphore extends TimerTask {
        
        public WhoisSemaphore() throws ProcessException {
            if (!WHOIS_QUERY_SEMAPHORE.tryAcquire()) {
                // Estouro de limite de consultas ao WHOIS.
                throw new ProcessException("ERROR: WHOIS QUERY LIMIT");
            }
        }
        
        @Override
        public void run() {
            WHOIS_QUERY_SEMAPHORE.release();
        }
    }
    
    /**
     * Adquire o direito a uma consulta comum no WHOIS.
     * Controla a taxa de 30 consultas no intervalo de 5 minutos.
     * @throws ProcessException se houver falha no processo.
     */
    private static void acquireWhoisQuery() throws ProcessException {
        WhoisSemaphore whoisSemaphore = new WhoisSemaphore();
        WHOIS_SEMAPHORE_TIMER.schedule(whoisSemaphore, 5 * 60 * 1000); // Libera o direito à consulta em 5 min.
        WHOIS_SEMAPHORE_TIMER.purge(); // Libera referências processadas.
    }
    
    /**
     * Semáphoro que controla o número máximo de consultas no WHOIS.
     * Controla a taxa de 30 consultas no intervalo de 24 horas.
     */
    private static final Semaphore WHOIS_ID_QUERY_SEMAPHORE = new Semaphore(30);
    
    /**
     * Classe de tarefa que adquire e libera o semáforo de consulta comum do WHOIS.
     * Controla a taxa de 30 consultas no intervalo de 24 horas.
     */
    private static class WhoisIDSemaphore extends TimerTask {
        
        public WhoisIDSemaphore() throws ProcessException {
            if (!WHOIS_ID_QUERY_SEMAPHORE.tryAcquire()) {
                // Estouro de limite de consultas ao WHOIS.
                throw new ProcessException("ERROR: WHOIS QUERY LIMIT");
            }
        }
        
        @Override
        public void run() {
            WHOIS_ID_QUERY_SEMAPHORE.release();
        }
    }
    
    /**
     * Adquire o direito a uma consulta de identificação no WHOIS.
     * Controla a taxa de 30 consultas no intervalo de 24 horas.
     * @throws ProcessException se houver falha no processo.
     */
    private static void acquireWhoisIDQuery() throws ProcessException {
        WhoisIDSemaphore whoisIDSemaphore = new WhoisIDSemaphore();
        WHOIS_SEMAPHORE_TIMER.schedule(whoisIDSemaphore, DAY_TIME); // Libera o direito à consulta em 24h.
        WHOIS_SEMAPHORE_TIMER.purge(); // Libera referências processadas.
    }
    
    /**
     * Semáphoro que controla o número máximo de conexões simutâneas no WHOIS.
     * Limite de 2 conexões simultâneas por IP de origem.
     */
    private static final Semaphore WHOIS_CONNECTION_SEMAPHORE = new Semaphore(2);
    
    /**
     * Consulta de identificação no WHOIS.
     * Controla a taxa de 30 consultas no intervalo de 24 horas.
     * @param query a consulta a ser realizada.
     * @param server o servidor que contém a informação.
     * @return o resultado do WHOIS para a consulta.
     * @throws ProcessException se houver falha no processamento da informação.
     */
    public static String whoisID(String query, String server) throws ProcessException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            WHOIS_CONNECTION_SEMAPHORE.acquire();
            try {
                acquireWhoisIDQuery();
                WhoisClient whoisClient = new WhoisClient();
                try {
                    whoisClient.connect(server);
                    InputStream inputStream = whoisClient.getInputStream(query);
                    int code;
                    while ((code = inputStream.read()) != -1) {
                        outputStream.write(code);
                    }
                } finally {
                    whoisClient.disconnect();
                }
            } finally {
                WHOIS_CONNECTION_SEMAPHORE.release();
            }
        } catch (ProcessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProcessException("ERROR: WHOIS CONNECTION FAIL", ex);
        }
        try {
            String result = outputStream.toString("ISO-8859-1");
            logWhois(server, query, result);
            return result;
        } catch (UnsupportedEncodingException ex) {
            throw new ProcessException("ERROR: ENCODING", ex);
        }
    }
    
    /**
     * Constante do servidor WHOIS brasileiro.
     */
    public static final String WHOIS_BR = "whois.nic.br";
    
    /**
     * Consulta de registros de nome de domínio.
     */
    public static InitialDirContext INITIAL_DIR_CONTEXT;
    
    
    static {
        try {
            Hashtable env = new Hashtable();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "3000");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            INITIAL_DIR_CONTEXT = new InitialDirContext(env);
        } catch (Exception ex) {
            Server.logError(ex);
            System.exit(1);
        }
    }
    
    /**
     * Consulta comum no WHOIS.
     * Controla a taxa de 30 consultas no intervalo de 5 minutos.
     * @param query a consulta a ser realizada.
     * @param server o servidor que contém a informação.
     * @return o resultado do WHOIS para a consulta.
     * @throws ProcessException se houver falha no processamento da informação.
     */
    public static String whois(String query, String server) throws ProcessException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            WHOIS_CONNECTION_SEMAPHORE.acquire();
            try {
                acquireWhoisQuery();
                WhoisClient whoisClient = new WhoisClient();
                try {
                    whoisClient.connect(server);
                    InputStream inputStream = whoisClient.getInputStream(query);
                    int code;
                    while ((code = inputStream.read()) != -1) {
                        outputStream.write(code);
                    }
                } finally {
                    whoisClient.disconnect();
                }
            } finally {
                WHOIS_CONNECTION_SEMAPHORE.release();
            }
        } catch (ProcessException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ProcessException("ERROR: WHOIS CONNECTION FAIL", ex);
        }
        try {
            String result = outputStream.toString("ISO-8859-1");
            logWhois(server, query, result);
            return result;
        } catch (UnsupportedEncodingException ex) {
            throw new ProcessException("ERROR: ENCODING", ex);
        }
    }
    
    public static synchronized void tryBackugroundRefresh() {
        // Evita que muitos processos fiquem 
        // presos aguardando a liberação do método.
        if (WHOIS_QUERY_SEMAPHORE.availablePermits() == 30) {
            backgroundRefresh();
        }
    }
    
    /**
     * Atualiza os registros quase expirando.
     */
    public static synchronized boolean backgroundRefresh() {
        if (WHOIS_QUERY_SEMAPHORE.availablePermits() == 30) {
            Server.logDebug("Refreshing cache...");
            if (Domain.backgroundRefresh()) {
                return true;
            } else if (Subnet.backgroundRefresh()) {
                return true;
            } else {
                return false;
            }
        } else {
            return false;
        }
    }
    
    /**
     * Processa a consulta e retorna o resultado.
     * @param query a expressão da consulta.
     * @return o resultado do processamento.
     */
    protected String processWHOIS(String query) {
        try {
            String result = "";
            if (query.length() == 0) {
                result = "ERROR: QUERY\n";
            } else {
                StringTokenizer tokenizer = new StringTokenizer(query, " ");
                String token = tokenizer.nextToken();
                boolean updated = false;
                if (token.equals("UPDATED")) {
                    token = tokenizer.nextToken();
                    updated = true;
                }
                if (Owner.isOwnerID(token) && tokenizer.hasMoreTokens()) {
                    Owner owner = Owner.getOwner(token);
                    while (tokenizer.hasMoreTokens()) {
                        String key = tokenizer.nextToken();
                        String value = owner.get(key, updated);
                        if (value == null) {
                            result += '\n';
                        } else {
                            result += value + '\n';
                        }
                    }
                } else if (Subnet.isValidIP(token) && tokenizer.hasMoreTokens()) {
                    Subnet subnet = Subnet.getSubnet(token);
                    while (tokenizer.hasMoreTokens()) {
                        String field = tokenizer.nextToken();
                        String value = subnet.get(field, updated);
                        if (value == null) {
                            result += "\n";
                        } else {
                            result += value + "\n";
                        }
                    }
                } else if (Domain.containsDomain(token) && tokenizer.hasMoreTokens()) {
                    Domain domain = Domain.getDomain(token);
                    while (tokenizer.hasMoreTokens()) {
                        String key = tokenizer.nextToken();
                        String value = domain.get(key, updated);
                        if (value == null) {
                            result += '\n';
                        } else {
                            result += value + '\n';
                        }
                    }
                } else {
                    result = "ERROR: QUERY\n";
                }
            }
            return result;
        } catch (ProcessException ex) {
            Server.logError(ex.getCause());
            return ex.getMessage() + "\n";
         } catch (Exception ex) {
            Server.logError(ex);
            return "ERROR: FATAL\n";
        }
    }
    
    public static final NumberFormat DECIMAL_FORMAT = NumberFormat.getNumberInstance();
    
    /**
     * Processa o comando e retorna o resultado.
     * @param command a expressão do comando.
     * @return o resultado do processamento.
     */
    protected String processCommand(String command) {
        try {
            String result = "";
            if (command.length() == 0) {
                result = "ERROR: COMMAND\n";
            } else {
                StringTokenizer tokenizer = new StringTokenizer(command, " ");
                String token = tokenizer.nextToken();
                if (token.equals("SHUTDOWN") && !tokenizer.hasMoreTokens()) {
                    // Comando para finalizar o serviço.
                    result = "OK\n";
                    shutdown();
                } else if (token.equals("STORE") && !tokenizer.hasMoreTokens()) {
                    // Comando para gravar o cache em disco.
                    result = "OK\n";
                    storeCache();
                } else if (token.equals("TDL") && tokenizer.hasMoreTokens()) {
                    // Comando para adicionar TDLs.
                    while (tokenizer.hasMoreTokens()) {
                        String tdl = tokenizer.nextToken();
                        Domain.addTDL(tdl);
                    }
                    result = "OK\n";
                } else if (token.equals("PROVIDER") && tokenizer.hasMoreTokens()) {
                    // Comando para adicionar provedor de e-mail.
                    while (tokenizer.hasMoreTokens()) {
                        String provider = tokenizer.nextToken();
                        SPF.addProvider(provider);
                    }
                    result = "OK\n";
                } else if (token.equals("GUESS") && tokenizer.hasMoreTokens()) {
                    // Comando para adicionar um palpite SPF.
                    String domain = tokenizer.nextToken();
                    int beginIndex = command.indexOf('"') + 1;
                    int endIndex = command.lastIndexOf('"');
                    if (beginIndex > 0 && endIndex > beginIndex) {
                        String spf = command.substring(beginIndex, endIndex);
                        SPF.addGuess(domain, spf);
                        result = "OK\n";
                    } else {
                        result = "ERROR: COMMAND\n";
                    }
                } else if (token.equals("REPUTATION")) {
                    // Comando para verificar a reputação dos tokens.
                    StringBuilder stringBuilder = new StringBuilder();
                    TreeMap<String,Distribution> distributionMap = SPF.getDistributionMap();
                    for (String tokenReputation : distributionMap.keySet()) {
                        Distribution distribution = distributionMap.get(tokenReputation);
                        float probability = distribution.getMinSpamProbability();
                        if (probability > 0.0f && distribution.hasFrequency()) {
                            Status status = distribution.getStatus();
                            String frequency = distribution.getFrequencyLiteral();
                            stringBuilder.append(tokenReputation);
                            stringBuilder.append(' ');
                            stringBuilder.append(frequency);
                            stringBuilder.append(' ');
                            stringBuilder.append(status);
                            stringBuilder.append(' ');
                            stringBuilder.append(DECIMAL_FORMAT.format(probability));
                            stringBuilder.append('\n');
                        }
                    }
                    result = stringBuilder.toString();
                } else if (token.equals("DROP") && tokenizer.hasMoreTokens()) {
                    // Comando para apagar registro em cache.
                    while (tokenizer.hasMoreTokens()) {
                        token = tokenizer.nextToken();
                        if (Owner.isOwnerID(token)) {
                            Owner.removeOwner(token);
                            result += "OK\n";
                        } else if (SubnetIPv4.isValidIPv4(token)) {
                            SubnetIPv4.removeSubnet(token);
                            result += "OK\n";
                        } else if (SubnetIPv6.isValidIPv6(token)) {
                            SubnetIPv6.removeSubnet(token);
                            result += "OK\n";
                        } else if (Domain.containsDomain(token)) {
                            Domain.removeDomain(token);
                            result += "OK\n";
                        } else {
                            result += "UNDEFINED\n";
                        }
                    }
                } else if (token.equals("DROPDISTRIBUTION") && tokenizer.hasMoreTokens()) {
                    while (tokenizer.hasMoreTokens()) {
                        token = tokenizer.nextToken();
                        SPF.dropDistribution(token);
                    }
                    result = "OK\n";
                } else if (token.equals("DROPTDL") && tokenizer.hasMoreTokens()) {
                    while (tokenizer.hasMoreTokens()) {
                        token = tokenizer.nextToken();
                        Domain.removeTDL(token);
                    }
                    result = "OK\n";
                } else if (token.equals("REFRESH") && tokenizer.hasMoreTokens()) {
                    // Comando para atualizar registro em cache.
                    while (tokenizer.hasMoreTokens()) {
                        token = tokenizer.nextToken();
                        if (Owner.isOwnerID(token)) {
                            Owner.refreshOwner(token);
                            result += "OK\n";
                        } else if (SubnetIPv4.isValidIPv4(token)) {
                            SubnetIPv4.refreshSubnet(token);
                            result += "OK\n";
                        } else if (SubnetIPv6.isValidIPv6(token)) {
                            SubnetIPv6.refreshSubnet(token);
                        } else if (Domain.containsDomain(token)) {
                            Domain.refreshDomain(token);
                            result += "OK\n";
                        } else {
                            result += "UNDEFINED\n";
                        }
                    }
                } else {
                    result = "ERROR: COMMAND\n";
                }
            }
            return result;
        } catch (ProcessException ex) {
            Server.logError(ex.getCause());
            return ex.getMessage() + "\n";
         } catch (Exception ex) {
            Server.logError(ex);
            return "ERROR: FATAL\n";
        }
    }
}
