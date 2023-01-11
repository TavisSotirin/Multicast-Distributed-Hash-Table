/*--------------------------------------------------------

1. Name / Date:
Tavis Sotirin-Miller
3/08/2020

2. Java version used, if not the official version for the class:

openjdk version "1.8.0_222"
OpenJDK Runtime Environment (AdoptOpenJDK)(build 1.8.0_222-b10)

3. Precise command-line compilation examples / instructions:

> javac McastDHT.java

4. Precise examples / instructions to run this program:

In separate shell windows:

> java McastDHT

All acceptable commands are displayed on the various consoles. Type 'help' to view them.

Defaultly runs on localhost only, so processes must be on the same machine. This could be updated with minor changes to the code as noted in the comments below

5. List of files needed for running the program.

 a. McastDHT.java

5. Notes:
I ran out of time for various reasons while working on this and was unable to finish, but I wanted to show what I had worked on at least so it didn't go to waste. I intend on finishing the network and reworking the messages so they are cleaner.

Any number (up to 999) of nodes can be added to the network and removed at will.

My node collision code is written but was not tested, as when the nodes are all on the same local network, my code won't allow collision between ports. I did write the code that I believe would work using just nodeID in an online environment, but was unable to test it locally.

My mcast code semi-works but I realized too late that I didn't set them up properly and instead of managing the mcast groups by node number (i.e. between node 10 and node 15, node 15 would handle any groups from 11-15, etc.),
my code would create a group with no 'root' and would instead continually forward a message until it got back to the original sender, not the root. No nodes were stored as active members of the casting group. 
The create, join, leave, and message functions work in my incorrect implementation. But the groups are setup fundamentally incorrectly I think.

As such, the commands do not work via the console. But I left the code in.

I don't think it would take me much longer to correctly implement the groups on top of my existing framework, but I have 30 minutes before the deadline so I'm submitting what I have.

This was fun though!

----------------------------------------------------------*/

import java.net.*;
import java.io.*;
import java.util.*;
import java.nio.*;
import java.util.regex.PatternSyntaxException;
import java.nio.charset.StandardCharsets;

public class McastDHT {
	static int nodeID = 1;
	static int comPort = 40000;
	static int successor = 1;
	static int predecessor = 1;
	static DatagramSocket mySocket;
	static InetAddress addr;
	static boolean canQuit = false;
	static ArrayList<Integer> knownNodes = new ArrayList<Integer>();
	static HashMap<Integer,Boolean> mcastGroups = new HashMap<Integer,Boolean>();
	// Used for loop/waiting management
	static boolean bWait = true;
	// Used to know if we successfully connected to the DHT at our requested nodeID
	static boolean bSetup = false;
	static Listener listener = null;
	
	public static void main(String[] args) {
		// Setup localhost address - for this assignment all nodes run locally, but to change them to run anywhere in the world would only require updating this value and some minor tweaks to the Message class to change the IP we send things to and storing the IP of known nodes
		try {
			addr = InetAddress.getLocalHost();
		} catch (UnknownHostException x) {return;}
		
		// Run initial setup - will only continue when succesfully connected to the network with a valid nodeID
		initialConnection();
		mcastGroups.put(-1,true);
		
		System.out.println("Successfully added to DHT\nNodeID: " + nodeID + "\tPredecessor: " + predecessor + "\tSuccessor: " + successor);
		
		// Wait for client input
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
		String input;
		String inArg1 = "";
		String inArg2 = "";
		String inCom = "";
		String printOut = "";
		boolean cont = true;
		

		// Basic user input switch to run different commands. Run this loop permanently for the rest of main's runtime, unless told to quit
		do {
			System.out.println("Enter command ('help' to see available list of commands): ");
			int groupID = -1;
			int tempNodeID = -1;
			try {
				input = in.readLine().trim();
				
				try {
					inCom = input.split("\\s+",3)[0].trim().toLowerCase();
					inArg1 = input.split("\\s+",3)[1].trim();
					inArg2 = input.split("\\s+",3)[2].trim();
				} catch (IndexOutOfBoundsException | PatternSyntaxException x) {
					try {
						inCom = input.split("\\s+",3)[0].trim().toLowerCase();
						inArg1 = input.split("\\s+",3)[1].trim();
						inArg2 = "";
					} catch (Exception y) {inCom = input.toLowerCase();}
				}
				
				switch (inCom) {
					case "help": 
						System.out.println("Status - Display nodeID, predecessor, successor");
						System.out.println("Ping [nodeID] - Send a ping to this nodeID");
						System.out.println("LoopPing [message] - Forward ping around DHT, all consoles will display [message]");
						System.out.println("Survey - Display all active DHT nodes, in order");
						//System.out.println("File [fileName] - Read .txt file with commands on each line, run them one by one");
						
						//System.out.println("Create [mcastID] - Create a new Mcast group with this ID - can be run up to 999 times");
						//System.out.println("Join [mcastID] [nodeID] - Add nodeID to Mcast group");
						//System.out.println("Send [mcastID] [message] - Send message to Mcast group");
						//System.out.println("Leave [mcastID] [nodeID] - Remove nodeID from Mcast group");
						//System.out.println("Destroy [mcastID] - Delete Mcast group");
						//System.out.println("Quit - Terminate this node and gracefully remove it from the DHT");
						break;
					case "status":
						System.out.println("NodeID: " + nodeID + "\tPredecessor: " + predecessor + "\tSuccessor: " + successor);
						break;
					case "survey":
						Message.rollCall(nodeID);
						while (bWait) {
							try {
								Thread.sleep(1);
							} catch (Exception x) {};
						}
						
						Collections.sort(knownNodes);

						printOut = "Active nodes: ";
						
						for (int node : knownNodes)
							printOut += node + ", ";
						
						printOut = printOut.substring(0,printOut.length()-2);
						
						System.out.println(printOut);
						bWait = true;
						break;
					// Ping a specific node
					case "ping":
						try {
							groupID = Integer.parseInt(inArg1);
						} catch (Exception x) {groupID = -1;}
						
						if (groupID == -1)
							System.out.println("Enter a valid node number to ping");
						else 
							Message.ping(nodeID,groupID);
						break;
					// LoopPing
					case "loopping":
						Message.loopPing(nodeID,inArg1 + " " + inArg2);
						break;
						
					// THIS CODE NEEDS TO BE UPDATED SO THAT NEWLY CREATED MCAST GROUPS ARE MANAGED AT A ROOT NODE
					// Join an mcast group
					/*
					case "join":
						// Parse requested groupID from input
						try {
							groupID = Integer.parseInt(inArg1);
							tempNodeID = Integer.parseInt(inArg2);
						} catch (Exception x) {groupID = -1;}
						
						if (groupID < 1 || groupID > 999)
							System.out.println("Enter a valid group number (between 1 and 999)");
						else {
							if (mcastGroups.containsKey(groupID)) {
								Message.mcastJoin(nodeID,groupID,nodeID);
								//mcastGroups.put(groupID,true);
								//System.out.println("Node added to MCast group " + groupID);
							}
							else 
								System.out.println("Group does not exist");
						}
						break;
					case "leave":
						try {
							groupID = Integer.parseInt(inArg1);
						} catch (Exception x) {groupID = -1;}
						
						if (groupID < 1 || groupID > 999)
							System.out.println("Enter a valid group number (between 1 and 999)");
						else {
							if (mcastGroups.containsKey(groupID)) {
								mcastGroups.put(groupID,false);
								System.out.println("Node left MCast group " + groupID);
							}
							else 
								System.out.println("Group does not exist");
						}
						break;
					case "create":
						try {
							groupID = Integer.parseInt(inArg1);
						} catch (Exception x) {groupID = -1;}
						
						if (groupID < 1 || groupID > 999)
							System.out.println("Enter a valid group number (between 1 and 999)");
						else {
							if (mcastGroups.containsKey(groupID))
								System.out.println("Group already exists");
							else {
								Message.mcastCreate(nodeID,groupID);
								System.out.println("MCast group " + groupID + " created.");
							}
						}
						break;
					case "destroy":
						try {
							groupID = Integer.parseInt(inArg1);
						} catch (Exception x) {groupID = -1;}
						
						if (groupID == -1)
							System.out.println("Enter a valid group number to destroy");
						else {
							if (mcastGroups.containsKey(groupID)) {
								Message.mcastDestroy(nodeID,groupID);
								System.out.println("MCast group " + groupID + " destroyed.");
							}
							else
								System.out.println("Group does not exist");
						}
						
						break;
					case "message":
						try {
							groupID = Integer.parseInt(inArg1);
						} catch (Exception x) {groupID = -1;}
						
						if (groupID == -1)
							System.out.println("Enter a valid group number");
						else {
							if (mcastGroups.containsKey(groupID))
								Message.mcastMessage(nodeID,groupID,inArg2);
							else
								System.out.println("Group does not exist");
						}
						
						break;
						*/
					case "quit":
						System.out.println("Server shutting down...");
						quit();
						break;
					default:
						System.out.println("Invalid entry");
				}
				
				if (canQuit)
					break;
				
				inArg1 = "";
				inArg2 = "";
				inCom = "";
				printOut = "";
			} catch (IOException x) {System.out.println("Error reading input from client. Try again.");}
		} while (cont);
		
		try {
			mySocket.close();
		} catch (Exception x) {};
		
		listener.bCont = false;
		listener = null;
		System.out.println("Successfully disconnected from DHT. Program will now close");
	}
	
	public static void connectNode() {
		comPort = 40000 + nodeID;
		
		try {
			mySocket = new DatagramSocket(comPort);
		} 
		catch (SocketException x) {
			nextComPort();
			connectNode();
		}
	}
	
	// Set random nodeID between 2 and 999
	public static void nextComPort() {
		Random rnum = new Random();
		nodeID = rnum.nextInt(998) + 2;
	}
	
	// Run for initial connection to request being added to DHT
	public static void initialConnection() {
		connectNode();
		listener = new Listener();
		listener.start();
		
		// Send initial hello message to see if our node is valid and to be added to the DHT
		Message.hello(nodeID,predecessor,successor,successor);
		
		// Wait until we recieve a response
		while (bWait) {
			try {
				Thread.sleep(1);
			} catch (Exception x) {};
		}
		
		// If we successfully were added, move on
		if (bSetup) {
			bWait = true;
			return;
		}
		// If we failed to get added, get a new nodeID and try again
		else {
			listener.bCont = false;
			bWait = true;
			nextComPort();
			initialConnection();
		}
	}
	
	// Wait until we recieve notice that we are allowed to leave the DHT, then update bool used in main loop
	public static void quit() {
		Message.goodbye(nodeID,predecessor,successor);
		while(!canQuit) {
			try {
				Thread.sleep(50);
			} catch (InterruptedException x) {};
		}
	}
}

// Listens for new packets until server shuts down
class Listener extends Thread{
	public static boolean bCont = true;
	
	public void run() {
		byte[] buffer = new byte[256];
		DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
		
		while(bCont) {
			try {
				McastDHT.mySocket.receive(packet);
				new Receiver(packet).start();
			} catch (IOException x) {};
		}
	}
}

// Basic message sending logic. All methods are basically the same, but provide some slight differences based on what needs to be sent
// UDP packets are sent with byte arrays composed using byteBuffers
class Message {
	static void hello(int sender, int pred, int succ, int port) {
		ByteBuffer buffer = ByteBuffer.allocate(16);
		buffer.putInt(0,sender);
		buffer.putInt(4,4);
		buffer.putInt(8,pred);
		buffer.putInt(12,succ);
		
		sendMessage(buffer.array(), port);
	}
	
	static void ping(int sender, int recipient) {
		ByteBuffer buffer = ByteBuffer.allocate(12);
		buffer.putInt(0,sender);
		buffer.putInt(4,0);
		buffer.putInt(8,recipient);
		
		sendMessage(buffer.array());
	}
	
	static void pingResponse(int sender, int recipient) {
		ByteBuffer buffer = ByteBuffer.allocate(12);
		buffer.putInt(0,sender);
		buffer.putInt(4,10);
		buffer.putInt(8,recipient);
		
		sendMessage(buffer.array(),sender);
	}
	
	static void mcastMessage(int sender, int groupID, String message) {
		ByteBuffer buffer = ByteBuffer.allocate(12 + message.length() * 2);
		buffer.putInt(0,sender);
		buffer.putInt(4,1);
		buffer.putInt(8,groupID);
		
		for (int i = 12; i < (12 + message.length() * 2); i += 2)
			buffer.putChar(i,message.toCharArray()[(i-12)/2]);
		
		sendMessage(buffer.array());
	}
	
	static void mcastCreate(int sender, int groupID) {
		ByteBuffer buffer = ByteBuffer.allocate(12);
		buffer.putInt(0,sender);
		buffer.putInt(4,2);
		buffer.putInt(8,groupID);
		
		sendMessage(buffer.array());
	}
	
	static void mcastDestroy(int sender, int groupID) {
		ByteBuffer buffer = ByteBuffer.allocate(12);
		buffer.putInt(0,sender);
		buffer.putInt(4,3);
		buffer.putInt(8,groupID);
		
		sendMessage(buffer.array());
	}
	
	static void error(int sender) {
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.putInt(0,sender);
		buffer.putInt(4,5);
		
		sendMessage(buffer.array());
	}
	
	static void mcastJoin(int sender, int groupID, int recipient) {
		ByteBuffer buffer = ByteBuffer.allocate(16);
		buffer.putInt(0,sender);
		buffer.putInt(4,7);
		buffer.putInt(8,groupID);
		buffer.putInt(12,recipient);
		
		sendMessage(buffer.array());
	}
	
	static void mcastResponse(int sender, int recipient, int port) {
		ByteBuffer buffer = ByteBuffer.allocate(12);
		buffer.putInt(0,sender);
		buffer.putInt(4,11);
		buffer.putInt(8,recipient);
		
		sendMessage(buffer.array(),port);
	}
	
	static void rollCall(int sender) {
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.putInt(0,sender);
		buffer.putInt(4,8);
		
		sendMessage(buffer.array());
	}
	
	static void rollCallResponse(int sender, int response) {
		ByteBuffer buffer = ByteBuffer.allocate(12);
		buffer.putInt(0,sender);
		buffer.putInt(4,9);
		buffer.putInt(8,response);
		
		sendMessage(buffer.array(),sender);
	}
	
	static void loopPing(int sender, String message) {
		ByteBuffer buffer = ByteBuffer.allocate(8 + message.length() * 2);
		buffer.putInt(0,sender);
		buffer.putInt(4,12);
		
		for (int i = 8; i < (8 + message.length() * 2); i += 2)
			buffer.putChar(i,message.toCharArray()[(i-8)/2]);
		
		sendMessage(buffer.array());
	}
	
	static void goodbye(int sender, int pred, int succ) {
		ByteBuffer buffer = ByteBuffer.allocate(16);
		buffer.putInt(0,sender);
		buffer.putInt(4,6);
		buffer.putInt(8,pred);
		buffer.putInt(12,succ);
		
		sendMessage(buffer.array());
	}
	
	private static void sendMessage(byte[] msg) {
		try {
			DatagramPacket packet = new DatagramPacket(msg, msg.length, McastDHT.addr, 40000 + McastDHT.successor);
			McastDHT.mySocket.send(packet);
		} catch (IOException x) {System.out.println("Error sending packet");}
	}
	
	// Special send for any messages that need to ensure the original node gets a response correctly
	private static void sendMessage(byte[] msg, int port) {
		try {
			DatagramPacket packet = new DatagramPacket(msg, msg.length, McastDHT.addr, 40000 + port);
			McastDHT.mySocket.send(packet);
		} catch (IOException x) {System.out.println("Error sending packet");}
	} 
}

// Spawned to manage each packet that comes through
class Receiver extends Thread {
	DatagramPacket packet;
	byte[] data;
	
	Receiver(DatagramPacket p) {this.packet = p; data = p.getData();}
	
	public void run() {
		// Set up local vars
		int msgSender = -1;
		int msgType = -1;
		int msgPred = -1;
		int msgSucc = -1;
		int msgGroupID = -1;
		int msgRecipient = -1;
		String msgData = "";
		
		// If for any reason a non compliant message is received, discard it
		try {
			// First 4 bytes of all messages are the ID of the original sender. Pull these bytes into a bytebuffer and translate into an int
			msgSender = ByteBuffer.wrap(Arrays.copyOfRange(data,0,4)).getInt();
			// Second 4 bytes of all messages are the message type. Pull these bytes into a bytebuffer and translate into an int
			msgType = ByteBuffer.wrap(Arrays.copyOfRange(data,4,8)).getInt();
		} catch (IndexOutOfBoundsException x) {return;}
		
		//System.out.println("Message sender: " + msgSender);
		//System.out.println("Message type: " + msgType);
		
		// Based on message type, pull data and preform different functions
		switch (msgType) {
			// Ping: Ping for a recipient node
			// Format: Sender  msgType  Recipient
			case 0:
				msgRecipient = ByteBuffer.wrap(Arrays.copyOfRange(data,8,12)).getInt();
				
				if (msgRecipient == McastDHT.nodeID) {
					System.out.println("Ping received from " + msgSender);
					Message.pingResponse(msgSender,msgRecipient);
				}
				else {
					Message.ping(msgSender,msgRecipient);
				}
				break;
				
			// MCast Message: Message to be shared with a particular MCast group. If we are a member, read it and pass it along, otherwise just pass it along
			// Format: Sender  msgType  MCastGroupID  Message
			case 1:
				msgGroupID = ByteBuffer.wrap(Arrays.copyOfRange(data,8,12)).getInt();
				
				byte[] msgBytesM = Arrays.copyOfRange(data,12,data.length);
				msgData = "";
				
				for (int i = 0; i < msgBytesM.length; i++) {
					if (i%2 == 1) {
						msgData += (char)(msgBytesM[i] & 0xFF);
					}
				}
				
				// If we haven't seen this group, add it to our list and set ourselves as not in it. Otherwise just return if we are in or not
				if (McastDHT.mcastGroups.compute(msgGroupID, (key, val) -> ((val == null || !val) ? false : true))) {
					// We are in the MCast group, receive the message
					System.out.println("MCast message for group " + msgGroupID + " received: \n" + msgData + "\n");
				}
				
				// Continue forwarding message if it hasn't made its way around yet
				if (msgSender == McastDHT.nodeID)
					break;
				else
					Message.mcastMessage(msgSender,msgGroupID,msgData);
				break;
			// MCast Create: Request to create a new MCast group
			// Format: Sender  msgType  MCastGroupID
			case 2:
				// Same logic as case 1 above, just without the message included
				msgGroupID = ByteBuffer.wrap(Arrays.copyOfRange(data,8,12)).getInt();
				
				McastDHT.mcastGroups.compute(msgGroupID, (key, val) -> ((val == null || !val) ? false : true));
				
				// Message made its way around the DHT
				if (msgSender == McastDHT.nodeID)
					break;
				
				Message.mcastCreate(msgSender,msgGroupID);
				break;
			// MCast Destroy: Request to destroy an existing MCast group
			// Format: Sender  msgType  MCastGroupID
			case 3:
				msgGroupID = ByteBuffer.wrap(Arrays.copyOfRange(data,8,12)).getInt();
				
				McastDHT.mcastGroups.remove(msgGroupID);
				
				// Message made its way around the DHT
				if (msgSender == McastDHT.nodeID)
					break;
				
				Message.mcastDestroy(msgSender,msgGroupID);
				break;
			// Hello Message: Initial node start up message - works like ping but with the chance to collide
			// Format: Sender  msgType [Predeccesor] [Successor]
			case 4:
				// Node collisions handled by node 1 - if node is in known list, send response to have it pick a new nodeID
				if (McastDHT.knownNodes.contains(msgSender)) {
					Message.error(msgSender);
					System.out.println("New node collision.");
					break;
				}
				else {
					McastDHT.knownNodes.add(msgSender);
				}
				
				msgPred = ByteBuffer.wrap(Arrays.copyOfRange(data,8,12)).getInt();
				msgSucc = ByteBuffer.wrap(Arrays.copyOfRange(data,12,16)).getInt();
				
				// If the sender node is us, the message has been successfully received and we were allowed to join the DHT with our requested nodeID
				if (msgSender == McastDHT.nodeID) {
					McastDHT.predecessor = msgPred;
					McastDHT.successor = msgSucc;
					
					// Connection successfull, main can continue
					McastDHT.bWait = false;
					McastDHT.bSetup = true;
					
					if (!McastDHT.knownNodes.contains(msgSucc)) {
						McastDHT.knownNodes.add(msgSucc);
					}
					if (!McastDHT.knownNodes.contains(msgPred)) {
						McastDHT.knownNodes.add(msgPred);
					}
					//Message.silentPing(McastDHT.nodeID);
				}
				// If the sender node is less than this node, it is our predecessor, and the message chain should stop. Send a final message to the original node telling it its predecessor and successor
				else if (msgSender < McastDHT.nodeID) {
					Message.hello(msgSender,McastDHT.predecessor,McastDHT.nodeID,msgSender);
					McastDHT.predecessor = msgSender;
				}
				// If the sender node is above this node, it is either our successor or further up the chain
				else if (msgSender > McastDHT.nodeID) {
					// If sender is greater than our successor and our successor is not 1, just keep forwarding the message
					if (msgSender > McastDHT.successor && McastDHT.successor != 1)
						Message.hello(msgSender,msgPred,msgSucc,McastDHT.successor);
					// Else if we are the last node in the chain, and the sender is above us, it is our new successor and we should notify it its done being added
					else if (McastDHT.successor == 1) {
						Message.hello(msgSender,McastDHT.nodeID,McastDHT.successor,msgSender);
						McastDHT.successor = msgSender;
					}
					// Finally if the sender is below our successor, it is our new successor. Update our successor and message our old successor of this node.
					else {
						Message.hello(msgSender,McastDHT.nodeID,McastDHT.successor,McastDHT.successor);
						McastDHT.successor = msgSender;
					}
				}
					
				break;
			// Error Message: NodeID collision message
			// Format: Sender  msgType
			case 5:
				if (msgSender == McastDHT.nodeID) {
					McastDHT.bWait = false;
					McastDHT.bSetup = false;
				}
				else {
					Message.error(msgSender);
				}
				break;
			// Goodbye Message: Node is leaving the DHT
			// Format: Sender  msgType  predecessor successor
			case 6:
				System.out.println(msgSender + " is quitting the network.");
				// If message made its way around, we can quit
				if (msgSender == McastDHT.nodeID) {
					McastDHT.canQuit = true;
					break;
				}
				
				msgPred = ByteBuffer.wrap(Arrays.copyOfRange(data,8,12)).getInt();
				msgSucc = ByteBuffer.wrap(Arrays.copyOfRange(data,12,16)).getInt();
				
				// Forward message along
				Message.goodbye(msgSender,msgPred,msgSucc);
				
				// Check to see if we need to update our successor or predecessor
				if (msgSender == McastDHT.successor)
					McastDHT.successor = msgSucc;
				else if (msgSender == McastDHT.predecessor)
					McastDHT.predecessor = msgPred;
				
				// Remove node from list of known nodes
				try {
					int tempIndex = McastDHT.knownNodes.lastIndexOf(msgSender);
					McastDHT.knownNodes.remove(tempIndex);
				} catch (IndexOutOfBoundsException x) {};
				
				break;
			// Mcast Join: Used to join a given node to an mcast group
			// Format: Sender msgType GroupID NodeID
			case 7:
				msgGroupID = ByteBuffer.wrap(Arrays.copyOfRange(data,8,12)).getInt();
				msgRecipient = ByteBuffer.wrap(Arrays.copyOfRange(data,12,16)).getInt();
				
				if (msgRecipient != McastDHT.nodeID) {
					Message.mcastJoin(msgSender,msgGroupID,msgRecipient);
					McastDHT.mcastGroups.compute(msgGroupID, (key, val) -> ((val == null || !val) ? false : true));
				}
				else {
					if (!McastDHT.mcastGroups.get(msgGroupID)) {
						McastDHT.mcastGroups.compute(msgGroupID, (key, val) -> true);
						System.out.println("This node has been added to MCast group: msgGroupID");
					}
					Message.mcastResponse(msgSender,McastDHT.nodeID,msgSender);
				}
				
				break;
			// RollCall: Used to ping every node in the network to update known nodes
			// Format: Sender  msgType
			case 8:
				// Do I know this node?
				if (!McastDHT.knownNodes.contains(msgSender)) {
					McastDHT.knownNodes.add(msgSender);
				}

				// Respond with our ID
				Message.rollCallResponse(msgSender,McastDHT.nodeID);
				
				// If message hasn't made it around, pass it on
				if (msgSender != McastDHT.nodeID) {
					Message.rollCall(msgSender);
				}
				else {
					McastDHT.bWait = false;
				}
				break;
			// RollCall Response: Used as a reply to a rollCall request
			// Format: Sender msgType Responder
			case 9:
				msgGroupID = ByteBuffer.wrap(Arrays.copyOfRange(data,8,12)).getInt();
				if (!McastDHT.knownNodes.contains(msgGroupID))
					McastDHT.knownNodes.add(msgGroupID);
				
				break;
			// PingResponse: Used to respond to a ping message
			case 10:
				msgRecipient = ByteBuffer.wrap(Arrays.copyOfRange(data,8,12)).getInt();
				System.out.println("Ping response received from node " + msgRecipient);
				break;
			// Mcast Response: Used to respond to mcast requests
			case 11:
				msgRecipient = ByteBuffer.wrap(Arrays.copyOfRange(data,8,12)).getInt();
				System.out.println("Mcast operation successfully performed on node " + msgRecipient);
				break;
			// LoopPing: Display message on all consoles until sender has been reached
			case 12:			
				byte[] msgBytesLoop = Arrays.copyOfRange(data,8,data.length);
				msgData = "";
				
				for (int i = 0; i < msgBytesLoop.length; i++) {
					if (i%2 == 1) {
						msgData += (char)(msgBytesLoop[i] & 0xFF);
					}
				}
				
				System.out.println("LoopPing message received:\n"+ msgData + "\n");
				
				// Continue forwarding message if it hasn't made its way around yet
				if (msgSender == McastDHT.nodeID)
					break;
				else
					Message.loopPing(msgSender,msgData);
				break;
			default:
				System.out.println("Unknown message recieved");
		}
	}
}