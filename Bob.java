// NIS 2021
// Bob (Server) Class that sends and receives data

import java.io.*;
import java.net.*;
import java.security.*;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

import javax.crypto.*;

import org.bouncycastle.cert.X509CertificateHolder;

import java.security.spec.RSAPrivateKeySpec;
import java.math.BigInteger;

class Bob {
    static boolean exit = false;
    static PublicKey BobPubKey;
    static PrivateKey BobPrivKey;
    static X509CertificateHolder certificate;

    public static void main(String[] args) throws Exception {
        
        // Certificate Generation
        // ========================================================
        genCertificate();
        byte[] messageDigest = sign(genDigest(certificate));
        byte[] publicKey = BobPubKey.getEncoded();
        
        System.out.println("Bob has started his day.\nWaiting for Alice to call...");
        /*
         * Create Server Socket: A server socket waits for requests to come in over the
         * network. It performs some operation based on that request, and then returns a
         * result to the requester.
         */
        int port = 888;
        ServerSocket serverSocket = new ServerSocket(port);
        String contactName = "Alice";

        /*
         * Connect to Client This class implements client sockets (also called just
         * "sockets"). A socket is an endpoint for communication between two
         * machines.connect it to client socket
         */
        Socket Alice = serverSocket.accept(); // security manager's checkAccept method would get called here.
        System.out.println("Connection established at " + Alice);

        // to send data to the client
        DataOutputStream sendStream = new DataOutputStream(Alice.getOutputStream());

        // to read data coming from the client
        DataInputStream dis = new DataInputStream(Alice.getInputStream());

        // to read data from the keyboard
        Scanner keyboardIn = new Scanner(System.in);

        System.out.println("Sending message digest and public key to Alice for TLS Handshake");
        sendStream.writeInt(messageDigest.length);
        sendStream.write(messageDigest);
        sendStream.writeInt(publicKey.length);
        sendStream.write(publicKey);

        System.out.println("..."); TimeUnit.SECONDS.sleep(1); System.out.println("..."); TimeUnit.SECONDS.sleep(1);
        System.out.println("Initiating secure chat...");
        TimeUnit.SECONDS.sleep(1);

        Thread sendMessage = new Thread(new Runnable() {
            String outMessage;

            @Override
            public void run() {
                while (!exit) {

                    try {
                        outMessage = keyboardIn.nextLine();
                        // Send message to Alice
                        sendStream.writeBytes(outMessage + "\n");

                        if (outMessage.equals("exit")) {
                            exit = true;
                            System.out.println("You left the chat.");
                        }

                        else if (outMessage.equals("!F")) {
                            String FILE_TO_SEND = "C:\\NISTestSend\\Capture.PNG";
                            // send File
                            File myFile = new File(FILE_TO_SEND);
                            byte[] mybytearray = new byte[(int) myFile.length()];
                            FileInputStream fis = new FileInputStream(myFile);
                            BufferedInputStream bis = new BufferedInputStream(fis);
                            bis.read(mybytearray, 0, mybytearray.length);
                            OutputStream os = Alice.getOutputStream();
                            System.out.println("Sending " + FILE_TO_SEND + "(" + mybytearray.length + " bytes)");
                            os.write(mybytearray, 0, mybytearray.length);
                            os.flush();
                            System.out.println("Done.");
                        }

                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

                System.exit(0);
            }
        });
        // readMessage thread
        Thread readMessage = new Thread(new Runnable() {
            String inMessage;

            @Override
            public void run() {

                while (!exit) {
                    try {
                        // read the message sent to this client
                        inMessage = dis.readLine();
                        if (!exit) {
                            if (inMessage.equals("exit")) {
                                Alice.close();
                                exit = true;
                                System.out.println("Alice left the chat.");
                            } else {
                                System.out.println(contactName + ": " + inMessage);
                            }
                        }
                    } catch (IOException e) {

                        e.printStackTrace();
                    }
                }

                System.exit(0);
            }
        });

        readMessage.start();
        sendMessage.start();

        /*
         * close connection sendStream.close(); dis.close(); keyboardIn.close();
         * serverSocket.close(); Alice.close();
         */

    }

    /*
     * // ================= Read private Key from the file=======================
     * 
     * readPrivateKeyFromFile method reads the RSA private key from private.key file
     * saved in same directory. the private key is used to decrypt/decipher the AES
     * key sent by Client.
     * 
     * 
     * 
     */

    PrivateKey readPrivateKey(String fileName) throws IOException {
        FileInputStream in = new FileInputStream(fileName);
        ObjectInputStream readObj = new ObjectInputStream(new BufferedInputStream(in));
        try {
            BigInteger m = (BigInteger) readObj.readObject();
            BigInteger d = (BigInteger) readObj.readObject();
            RSAPrivateKeySpec keySpec = new RSAPrivateKeySpec(m, d);
            KeyFactory fact = KeyFactory.getInstance("RSA");
            PrivateKey privateKey = fact.generatePrivate(keySpec);
            return privateKey;
        } catch (Exception e) {
            throw new RuntimeException("Some error in reading private key", e);
        } finally {
            readObj.close();
        }
    }

    public static void genCertificate() throws Exception {
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance("RSA"); // create RSA KeyPairGenerator
        kpGen.initialize(2048, new SecureRandom()); // Choose key strength
        KeyPair keyPair = kpGen.generateKeyPair(); // Generate private and public keys
        BobPubKey = keyPair.getPublic(); // PubKey of the CA
        BobPrivKey = keyPair.getPrivate();

        System.out.println("Populating certificate values...");
        TimeUnit.SECONDS.sleep(1);

        CertificateAuthority CA = new CertificateAuthority();
        CA.setOutFile("Bob.cert");
        CA.setSubject("Bob");
        CA.generateSerial();
        CA.setSubjectPubKey(BobPubKey);

        CA.populateCert();

        CA.generateCert();
        certificate = CA.getCertificate();
        System.out.println("Bob certicate signed and generated. See Bob.cert");

        CA.savePubKey();
        CA.savePrivKey();

    }

    public static byte[] genDigest(X509CertificateHolder cert) throws InvalidKeyException, NoSuchAlgorithmException,
            NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, IOException {
        byte[] input = cert.getEncoded();
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        md.update(input);
        byte[] digest = md.digest();
        return digest;

    }

    public static byte[] sign(byte[] input) throws InvalidKeyException, NoSuchAlgorithmException,
            NoSuchPaddingException, IllegalBlockSizeException, BadPaddingException, IOException {

        Cipher cipher = Cipher.getInstance("RSA/ECB/PKCS1Padding");

        // Initializing a Cipher object
        cipher.init(Cipher.ENCRYPT_MODE, BobPrivKey);

        // Adding data to the cipher

        cipher.update(input);

        // encrypting the data
        byte[] cipherText = cipher.doFinal();
        return cipherText;
    }

}
