// NIS 2021
// Bob (Server) Class that sends and receives data

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Scanner;
import java.security.spec.X509EncodedKeySpec;

import org.bouncycastle.asn1.x509.SubjectPublicKeyInfo;
import org.bouncycastle.cert.X509CertificateHolder;



class Bob {
    private static boolean exit = false;
    private static PublicKey BobPubKey;
    private static PrivateKey BobPrivKey;
    private static X509CertificateHolder certificate;
    private static PrivateKey CAPrivKey;
    private static PublicKey CAPubKey;
    private static PublicKey AlicePubKey;

    public static void main(String[] args) throws Exception {

        // Certificate Generation
        // ========================================================
        System.out.println("Generating public and private keys...");
        // TimeUnit.SECONDS.sleep(1);
        genCertificate();
        System.out.println("Bob is up and running.");
        // TimeUnit.SECONDS.sleep(1);
        System.out.println("Waiting for Alice to connect...");
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
        DataOutputStream dos = new DataOutputStream(Alice.getOutputStream());

        // to read data coming from the client
        DataInputStream dis = new DataInputStream(Alice.getInputStream());

        // to read data from the keyboard
        Scanner keyboard = new Scanner(System.in);

        byte[] messageDigest = RSA.sign(CertificateAuthority.genDigest(certificate), CAPrivKey);
        byte[] certEncoded = certificate.getEncoded();

        System.out.println("Sending message digest to Alice for TLS Handshake");
        dos.writeInt(messageDigest.length);
        dos.write(messageDigest);

        // Receive Message Digest
        int byteLength = dis.readInt();
        byte[] inmessageDigest = new byte[byteLength];
        dis.readFully(inmessageDigest);
        System.out.println("Alice message Digest received");
        // TimeUnit.SECONDS.sleep(1);

        System.out.println("Sending certifificate to Alice for TLS Handshake");
        dos.writeInt(certEncoded.length);
        dos.write(certEncoded);

        byteLength = dis.readInt();
        byte[] cert = new byte[byteLength];
        dis.readFully(cert);
        X509CertificateHolder AliceCert = new X509CertificateHolder(cert);
        SubjectPublicKeyInfo tempCert = AliceCert.getSubjectPublicKeyInfo();
        byte[] tempArray = tempCert.getEncoded();

        X509EncodedKeySpec spec = new X509EncodedKeySpec(tempArray);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        AlicePubKey = kf.generatePublic(spec);
        System.out.println("Alice certificate received");
        // TimeUnit.SECONDS.sleep(1);

        // Bob must now compare her message digest to Bob's message digest.
        byte[] BobDigest = CertificateAuthority.genDigest(AliceCert);

        if (RSA.authenticate(BobDigest, inmessageDigest, CAPubKey)) {
            // TimeUnit.SECONDS.sleep(1);
            System.out.println("Bob's digest matches Alice's.");
            if (certificate.getIssuer().equals(AliceCert.getIssuer())) {
                // TimeUnit.SECONDS.sleep(1);
                System.out.println("Bob trusts the CA of Alice's certificate.");

            }

        }

        else {
            System.out.println("This connection is not safe.");
            System.exit(0);
        }

        System.out.println("...");
        // TimeUnit.SECONDS.sleep(1);
        System.out.println("...");
        // TimeUnit.SECONDS.sleep(1);
        System.out.println("Initiating secure chat...");
        // TimeUnit.SECONDS.sleep(1);

        Thread sendMessage = new Thread(new Runnable() {

            @Override
            public void run() {
                while (!exit) {

                    String msg = keyboard.nextLine();
                    byte[] encodedmsg = msg.getBytes(StandardCharsets.UTF_8);
                    byte[] PGPcipher;

                    try {

                        PGPcipher = PGP.encrypt(encodedmsg, AlicePubKey, BobPrivKey);
                        dos.writeInt(PGP.getIVLength());
                        dos.writeInt(PGP.getSessionKeyLength());
                        dos.writeInt(PGP.getAESLength());
                        dos.writeInt(PGP.getHashLength());
                        dos.writeInt(PGP.getMessageLength());
                        dos.writeInt(PGPcipher.length);
                        dos.write(PGPcipher);

                        if (msg.equals("exit")) {
                            exit = true;
                            System.out.println("You left the chat.");
                        }

                        else if (msg.equals("!F")) {
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

                    } catch (Exception e) {
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
                        if (!exit) {
                            int IVLength = dis.readInt();
                            int skLength = dis.readInt();
                            int AESLength = dis.readInt(); 
                            int hashLength = dis.readInt(); 
                            int messageLength = dis.readInt(); 
                            int length = dis.readInt();
                            byte[] inCipher = new byte[length];
                            dis.readFully(inCipher);
                            String plaintext = PGP.decrypt(inCipher, BobPrivKey, AlicePubKey, IVLength, skLength, AESLength, hashLength,
                                    messageLength);
                            // byte[] plaintext = AES.AESDecryption(AESdecrypt, sk, IV)
                            inMessage = plaintext.toString();

                            if (inMessage.equals("exit")) {
                                Alice.close();
                                exit = true;
                                System.out.println("Alice left the chat.");
                            } else {
                                System.out.println(contactName + ": " + inMessage);
                            }
                        }
                    } catch ( Exception e) {

                        e.printStackTrace();
                    }
                }
                keyboard.close();
                System.exit(0);
            }
        });

        readMessage.start();
        sendMessage.start();

    }

    public static void genCertificate() throws Exception {
        KeyPairGenerator kpGen = KeyPairGenerator.getInstance("RSA"); // create RSA KeyPairGenerator
        kpGen.initialize(2048, new SecureRandom()); // Choose key strength
        KeyPair keyPair = kpGen.generateKeyPair(); // Generate private and public keys
        BobPubKey = keyPair.getPublic(); // PubKey of the CA
        BobPrivKey = keyPair.getPrivate();

        System.out.println("Populating certificate values...");

        CertificateAuthority CA = new CertificateAuthority();
        CA.setOutFile("./certs/Bob.cert");
        CA.setSubject("Bob");
        CA.generateSerial();
        CA.setSubjectPubKey(BobPubKey);

        CA.populateCert();

        CA.generateCert();
        certificate = CA.getCertificate();
        System.out.println("Bob certicate signed and generated. See Bob.cert");

        CAPubKey = CA.savePubKey();
        CAPrivKey = CA.savePrivKey();

    }

    

}
