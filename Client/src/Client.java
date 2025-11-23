import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

/**
 * Chat Client
 * A Swing-based chat application supporting text, image, voice notes, and
 * real-time calls.
 */
public class Client extends JFrame {
    private static final String SERVER_IP = "127.0.0.1";
    private static final int SERVER_PORT = 8889;

    // --- App Colors (Red Theme) ---
    private static final Color APP_RED = new Color(220, 53, 69);
    private static final Color APP_DARK_RED = new Color(139, 0, 0);
    private static final Color APP_LIGHT_RED = new Color(255, 218, 224);
    private static final Color APP_GRAY = new Color(240, 240, 240);
    private static final Color APP_DARK_GRAY = new Color(42, 57, 66);
    private static final Color APP_BUBBLE_RECEIVED = Color.WHITE;
    private static final Color APP_BUBBLE_SENT = new Color(220, 53, 69);

    // --- Protocol Constants ---
    private static final byte TYPE_TEXT = 1;
    private static final byte TYPE_IMAGE = 2;
    private static final byte TYPE_AUDIO = 3;
    private static final byte TYPE_VOICE_STREAM = 4;
    private static final byte TYPE_CALL_REQUEST = 6;
    private static final byte TYPE_CALL_ACCEPT = 7;
    private static final byte TYPE_CALL_DECLINE = 8;
    private static final byte TYPE_CALL_END = 9;
    private static final byte TYPE_GROUP_CALL_REQUEST = 10;

    // --- Networking ---
    private Socket socket;
    private DataOutputStream out;
    private DataInputStream in;

    // --- UI Components ---
    private JPanel chatPanel;
    private JScrollPane scrollPane;
    private JTextField inputField;
    private JButton sendButton, attachButton, voiceButton, callButton, groupCallButton;
    private JLabel chatHeaderLabel;

    // --- State ---
    private boolean isRecording = false;
    private AtomicBoolean isCalling = new AtomicBoolean(false);
    private int currentCallTarget = 0;

    // --- Audio ---
    private TargetDataLine microphone;
    private SourceDataLine speakers;
    private Clip ringtoneClip;

    public Client() {
        super("Chat App");
        setSize(1100, 700);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // Ignore look and feel errors
        }

        // Main Container
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Note: Left Sidebar removed as per user request.

        // --- Right Chat Area ---
        JPanel chatArea = new JPanel(new BorderLayout());

        // 1. Chat Header
        JPanel chatHeader = new JPanel(new BorderLayout());
        chatHeader.setBackground(APP_DARK_RED);
        chatHeader.setBorder(new EmptyBorder(10, 15, 10, 15));
        chatHeader.setPreferredSize(new Dimension(0, 60));

        chatHeaderLabel = new JLabel("👥 Group Chat");
        chatHeaderLabel.setForeground(Color.WHITE);
        chatHeaderLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 16));
        chatHeader.add(chatHeaderLabel, BorderLayout.WEST);

        // Header Buttons (Call & Group Call)
        JPanel headerButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        headerButtons.setOpaque(false);

        callButton = createButton("Call", "📞", APP_DARK_RED);
        callButton.setToolTipText("Voice Call (Direct)");
        callButton.addActionListener(e -> initiateCall());

        groupCallButton = createButton("Group Call", "📢", APP_DARK_RED);
        groupCallButton.setToolTipText("Group Voice Call (All Users)");
        groupCallButton.addActionListener(e -> initiateGroupCall());

        headerButtons.add(callButton);
        headerButtons.add(groupCallButton);
        chatHeader.add(headerButtons, BorderLayout.EAST);

        chatArea.add(chatHeader, BorderLayout.NORTH);

        // 2. Chat Messages Area
        chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        chatPanel.setBackground(new Color(230, 221, 212));
        chatPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        scrollPane = new JScrollPane(chatPanel);
        scrollPane.setBorder(null);
        scrollPane.getVerticalScrollBar().setUnitIncrement(16);
        chatArea.add(scrollPane, BorderLayout.CENTER);

        // 3. Input Area
        JPanel inputArea = new JPanel(new BorderLayout(10, 0));
        inputArea.setBackground(APP_GRAY);
        inputArea.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Left buttons (Attach)
        JPanel leftButtons = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        leftButtons.setOpaque(false);

        attachButton = createButton("Attach", "🖼️", APP_GRAY);
        attachButton.setToolTipText("Attach Image");
        attachButton.addActionListener(e -> sendImage());
        leftButtons.add(attachButton);

        inputArea.add(leftButtons, BorderLayout.WEST);

        // Input Field
        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.setBorder(new EmptyBorder(10, 10, 10, 10));
        inputField.addActionListener(e -> sendText());
        inputArea.add(inputField, BorderLayout.CENTER);

        // Right buttons (Voice & Send)
        JPanel rightButtons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightButtons.setOpaque(false);

        voiceButton = createButton("Record", "🎙️", APP_GRAY);
        voiceButton.setToolTipText("Hold to Record");
        voiceButton.addMouseListener(new MouseAdapter() {
            public void mousePressed(MouseEvent e) {
                startRecording();
            }

            public void mouseReleased(MouseEvent e) {
                stopRecording();
            }
        });

        sendButton = createButton("Send", "📤", APP_RED);
        sendButton.setToolTipText("Send");
        sendButton.addActionListener(e -> sendText());

        rightButtons.add(voiceButton);
        rightButtons.add(sendButton);
        inputArea.add(rightButtons, BorderLayout.EAST);

        chatArea.add(inputArea, BorderLayout.SOUTH);

        mainPanel.add(chatArea, BorderLayout.CENTER);
        add(mainPanel);

        connectToServer();
        setVisible(true);
    }

    /**
     * Helper to create styled buttons with text and icons.
     */
    private JButton createButton(String text, String icon, Color bg) {
        JButton btn = new JButton(icon + " " + text);
        btn.setFont(new Font(Font.DIALOG, Font.BOLD, 14));
        btn.setBackground(bg);
        btn.setForeground(bg == APP_RED ? Color.WHITE : Color.GRAY);
        btn.setFocusPainted(false);
        btn.setBorderPainted(false);
        btn.setCursor(new Cursor(Cursor.HAND_CURSOR));
        btn.setBorder(new EmptyBorder(8, 15, 8, 15));
        return btn;
    }

    // --- Networking Methods ---

    private void connectToServer() {
        try {
            socket = new Socket(SERVER_IP, SERVER_PORT);
            out = new DataOutputStream(socket.getOutputStream());
            in = new DataInputStream(socket.getInputStream());
            new Thread(this::listenForMessages).start();
            addSystemMessage("✓ Connected");
        } catch (IOException e) {
            addSystemMessage("✗ Connection failed: " + e.getMessage());
        }
    }

    private void listenForMessages() {
        try {
            while (true) {
                byte type = in.readByte();
                int senderId = in.readInt();
                int length = in.readInt();
                byte[] body = new byte[length];
                in.readFully(body);

                SwingUtilities.invokeLater(() -> {
                    try {
                        handleMessage(type, senderId, body);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
            }
        } catch (IOException e) {
            addSystemMessage("✗ Disconnected");
        }
    }

    private void handleMessage(byte type, int senderId, byte[] body) throws Exception {
        String userLabel = "User " + senderId;

        switch (type) {
            case TYPE_TEXT:
                String text = new String(body, "UTF-8");
                if (senderId == 0) {
                    addSystemMessage(text);
                } else {
                    addMessageBubble(userLabel, text, false);
                }
                break;
            case TYPE_IMAGE:
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(body));
                addImageBubble(userLabel, img, false);
                break;
            case TYPE_AUDIO:
                addAudioBubble(userLabel, body, false);
                break;
            case TYPE_VOICE_STREAM:
                playStreamedAudio(body);
                break;
            case TYPE_CALL_REQUEST:
                handleIncomingCall(senderId, false);
                break;
            case TYPE_GROUP_CALL_REQUEST:
                handleIncomingCall(senderId, true);
                break;
            case TYPE_CALL_ACCEPT:
                handleCallAccepted(senderId);
                break;
            case TYPE_CALL_DECLINE:
                handleCallDeclined(senderId);
                break;
            case TYPE_CALL_END:
                endCall(false);
                break;
        }
    }

    // --- Call Signaling ---

    private void initiateCall() {
        String targetStr = JOptionPane.showInputDialog(this, "Enter User ID to call:");
        if (targetStr != null && !targetStr.isEmpty()) {
            try {
                int targetId = Integer.parseInt(targetStr);
                currentCallTarget = targetId;
                sendMessage(TYPE_CALL_REQUEST, targetId, new byte[0]);
                addSystemMessage("📞 Calling User " + targetId + "...");
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Invalid User ID");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void initiateGroupCall() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Start a Group Voice Call with everyone?",
                "Group Call",
                JOptionPane.YES_NO_OPTION);

        if (choice == JOptionPane.YES_OPTION) {
            try {
                currentCallTarget = 0; // Broadcast target
                sendMessage(TYPE_GROUP_CALL_REQUEST, 0, new byte[0]);
                addSystemMessage("📢 Starting Group Call...");
                startCallSession();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void handleIncomingCall(int senderId, boolean isGroup) {
        startRinging();
        String title = isGroup ? "📢 Group Call" : "📞 Incoming Call";
        String msg = isGroup ? "📢 Group Call from User " + senderId : "📞 Incoming Call from User " + senderId;

        int choice = JOptionPane.showConfirmDialog(this,
                msg,
                title,
                JOptionPane.YES_NO_OPTION);

        stopRinging();

        try {
            if (choice == JOptionPane.YES_OPTION) {
                currentCallTarget = isGroup ? 0 : senderId; // 0 for Group, Sender for Direct
                sendMessage(TYPE_CALL_ACCEPT, senderId, new byte[0]);
                startCallSession();
            } else {
                sendMessage(TYPE_CALL_DECLINE, senderId, new byte[0]);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void handleCallAccepted(int senderId) {
        addSystemMessage("✓ User " + senderId + " accepted");
        startCallSession();
    }

    private void handleCallDeclined(int senderId) {
        addSystemMessage("✗ User " + senderId + " declined");
        currentCallTarget = 0;
    }

    private void startCallSession() {
        isCalling.set(true);
        callButton.setEnabled(false);
        groupCallButton.setText("📵 End");
        groupCallButton.setBackground(Color.RED);

        // Remove listeners from groupCallButton for End Call action
        for (ActionListener al : groupCallButton.getActionListeners())
            groupCallButton.removeActionListener(al);

        groupCallButton.addActionListener(e -> {
            try {
                sendMessage(TYPE_CALL_END, currentCallTarget, new byte[0]);
                endCall(true);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        });

        new Thread(this::streamVoice).start();
    }

    private void endCall(boolean local) {
        isCalling.set(false);
        callButton.setEnabled(true);
        groupCallButton.setText("📢 Group Call");
        groupCallButton.setBackground(APP_DARK_RED);

        for (ActionListener al : groupCallButton.getActionListeners())
            groupCallButton.removeActionListener(al);
        groupCallButton.addActionListener(e -> initiateGroupCall());

        if (!local)
            addSystemMessage("Call ended");
        currentCallTarget = 0;
    }

    /**
     * Plays a digital phone ringtone.
     */
    private void startRinging() {
        try {
            // Digital Phone Ring (Two tones, silence)
            byte[] tone = new byte[32000]; // 2 seconds
            for (int i = 0; i < tone.length; i++) {
                // 0-0.4s: Tone 1 (800Hz)
                // 0.4-0.5s: Silence
                // 0.5-0.9s: Tone 2 (1000Hz)
                // 0.9-2.0s: Silence
                double t = i / 16000.0;
                if (t < 0.4) {
                    tone[i] = (byte) (Math.sin(2 * Math.PI * 800 * t) * 127);
                } else if (t >= 0.5 && t < 0.9) {
                    tone[i] = (byte) (Math.sin(2 * Math.PI * 1000 * t) * 127);
                } else {
                    tone[i] = 0;
                }
            }
            AudioFormat format = new AudioFormat(16000, 8, 1, true, true);
            DataLine.Info info = new DataLine.Info(Clip.class, format);
            ringtoneClip = (Clip) AudioSystem.getLine(info);
            ringtoneClip.open(format, tone, 0, tone.length);
            ringtoneClip.loop(Clip.LOOP_CONTINUOUSLY);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopRinging() {
        if (ringtoneClip != null) {
            ringtoneClip.stop();
            ringtoneClip.close();
        }
    }

    // --- UI Helpers ---

    private void addMessageBubble(String sender, String text, boolean isMe) {
        JPanel container = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 5, 5));
        container.setOpaque(false);

        JPanel bubble = new JPanel(new BorderLayout());
        bubble.setBackground(isMe ? APP_BUBBLE_SENT : APP_BUBBLE_RECEIVED);
        bubble.setBorder(new CompoundBorder(
                new LineBorder(isMe ? APP_BUBBLE_SENT : APP_BUBBLE_RECEIVED, 1, true),
                new EmptyBorder(8, 12, 8, 12)));
        bubble.setMaximumSize(new Dimension(400, Integer.MAX_VALUE));

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);

        if (!isMe) {
            JLabel nameLabel = new JLabel(sender);
            nameLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 11));
            nameLabel.setForeground(APP_RED);
            content.add(nameLabel, BorderLayout.NORTH);
        }

        JTextArea textArea = new JTextArea(text);
        textArea.setWrapStyleWord(true);
        textArea.setLineWrap(true);
        textArea.setEditable(false);
        textArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        textArea.setBackground(isMe ? APP_BUBBLE_SENT : APP_BUBBLE_RECEIVED);
        textArea.setForeground(isMe ? Color.WHITE : Color.BLACK);
        textArea.setBorder(null);
        content.add(textArea, BorderLayout.CENTER);

        JLabel timeLabel = new JLabel(new SimpleDateFormat("HH:mm").format(new Date()));
        timeLabel.setFont(new Font("Segoe UI", Font.PLAIN, 10));
        timeLabel.setForeground(isMe ? new Color(240, 240, 240) : Color.GRAY);
        timeLabel.setHorizontalAlignment(SwingConstants.RIGHT);
        content.add(timeLabel, BorderLayout.SOUTH);

        bubble.add(content);
        container.add(bubble);

        chatPanel.add(container);
        scrollToBottom();
    }

    private void addImageBubble(String sender, BufferedImage img, boolean isMe) {
        JPanel container = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 5, 5));
        container.setOpaque(false);

        int maxWidth = 250;
        int newHeight = (img.getHeight() * maxWidth) / img.getWidth();
        Image scaled = img.getScaledInstance(maxWidth, newHeight, Image.SCALE_SMOOTH);

        JPanel bubble = new JPanel(new BorderLayout());
        bubble.setBackground(isMe ? APP_BUBBLE_SENT : APP_BUBBLE_RECEIVED);
        bubble.setBorder(new EmptyBorder(5, 5, 5, 5));

        if (!isMe) {
            JLabel nameLabel = new JLabel("👤 " + sender);
            nameLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 11));
            nameLabel.setForeground(APP_RED);
            nameLabel.setBorder(new EmptyBorder(5, 5, 2, 5));
            bubble.add(nameLabel, BorderLayout.NORTH);
        }

        JLabel imgLabel = new JLabel(new ImageIcon(scaled));
        bubble.add(imgLabel, BorderLayout.CENTER);

        container.add(bubble);
        chatPanel.add(container);
        scrollToBottom();
    }

    private void addAudioBubble(String sender, byte[] audioData, boolean isMe) {
        JPanel container = new JPanel(new FlowLayout(isMe ? FlowLayout.RIGHT : FlowLayout.LEFT, 5, 5));
        container.setOpaque(false);

        JPanel bubble = new JPanel(new BorderLayout());
        bubble.setBackground(isMe ? APP_BUBBLE_SENT : APP_BUBBLE_RECEIVED);
        bubble.setBorder(new EmptyBorder(8, 12, 8, 12));

        if (!isMe) {
            JLabel nameLabel = new JLabel("👤 " + sender);
            nameLabel.setFont(new Font(Font.DIALOG, Font.BOLD, 11));
            nameLabel.setForeground(APP_RED);
            bubble.add(nameLabel, BorderLayout.NORTH);
        }

        JButton playBtn = new JButton("▶ Voice Message");
        playBtn.setFont(new Font(Font.DIALOG, Font.PLAIN, 12));
        playBtn.setBackground(isMe ? APP_BUBBLE_SENT : APP_BUBBLE_RECEIVED);
        playBtn.setForeground(isMe ? Color.WHITE : APP_RED);
        playBtn.setFocusPainted(false);
        playBtn.setBorderPainted(false);

        playBtn.addActionListener(new ActionListener() {
            Clip clip;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (clip != null && clip.isRunning()) {
                    clip.stop();
                    clip.close();
                    playBtn.setText("▶ Voice Message");
                } else {
                    try {
                        AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
                        clip = AudioSystem.getClip();
                        clip.open(format, audioData, 0, audioData.length);
                        clip.addLineListener(event -> {
                            if (event.getType() == LineEvent.Type.STOP) {
                                clip.close();
                                playBtn.setText("▶ Voice Message");
                            }
                        });
                        clip.start();
                        playBtn.setText("⏹ Stop");
                    } catch (Exception ex) {
                        ex.printStackTrace();
                    }
                }
            }
        });

        bubble.add(playBtn, BorderLayout.CENTER);
        container.add(bubble);
        chatPanel.add(container);
        scrollToBottom();
    }

    private void addSystemMessage(String text) {
        JPanel container = new JPanel(new FlowLayout(FlowLayout.CENTER));
        container.setOpaque(false);

        JLabel label = new JLabel("ℹ️ " + text);
        label.setFont(new Font(Font.DIALOG, Font.ITALIC, 11));
        label.setForeground(new Color(134, 150, 160));
        label.setBackground(new Color(255, 255, 255, 200));
        label.setOpaque(true);
        label.setBorder(new EmptyBorder(4, 10, 4, 10));

        container.add(label);
        chatPanel.add(container);
        scrollToBottom();
    }

    private void scrollToBottom() {
        chatPanel.revalidate();
        SwingUtilities.invokeLater(() -> {
            JScrollBar vertical = scrollPane.getVerticalScrollBar();
            vertical.setValue(vertical.getMaximum());
        });
    }

    // --- Actions ---

    private void sendText() {
        String text = inputField.getText().trim();
        if (text.isEmpty())
            return;
        try {
            sendMessage(TYPE_TEXT, 0, text.getBytes("UTF-8"));
            addMessageBubble("You", text, true);
            inputField.setText("");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void sendImage() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            try {
                BufferedImage img = ImageIO.read(chooser.getSelectedFile());
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(img, "png", baos);
                sendMessage(TYPE_IMAGE, 0, baos.toByteArray());
                addImageBubble("You", img, true);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendMessage(byte type, int targetId, byte[] body) throws IOException {
        synchronized (out) {
            out.writeByte(type);
            out.writeInt(targetId);
            out.writeInt(body.length);
            out.write(body);
            out.flush();
        }
    }

    // --- Audio Recording ---

    private void startRecording() {
        isRecording = true;
        voiceButton.setBackground(Color.RED);
        new Thread(() -> {
            try {
                AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
                DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
                TargetDataLine line = (TargetDataLine) AudioSystem.getLine(info);
                line.open(format);
                line.start();

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[1024];
                while (isRecording) {
                    int count = line.read(buffer, 0, buffer.length);
                    if (count > 0)
                        baos.write(buffer, 0, count);
                }
                line.stop();
                line.close();
                byte[] audioData = baos.toByteArray();
                sendMessage(TYPE_AUDIO, 0, audioData);
                SwingUtilities.invokeLater(() -> addAudioBubble("You", audioData, true));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();
    }

    private void stopRecording() {
        isRecording = false;
        voiceButton.setBackground(APP_GRAY);
    }

    // --- Voice Call Streaming ---

    private void streamVoice() {
        try {
            AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
            TargetDataLine line = AudioSystem.getTargetDataLine(format);
            line.open(format);
            line.start();

            byte[] buffer = new byte[4096];
            while (isCalling.get()) {
                int count = line.read(buffer, 0, buffer.length);
                if (count > 0) {
                    byte[] chunk = new byte[count];
                    System.arraycopy(buffer, 0, chunk, 0, count);
                    sendMessage(TYPE_VOICE_STREAM, currentCallTarget, chunk);
                }
            }
            line.stop();
            line.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void playStreamedAudio(byte[] chunk) {
        try {
            if (speakers == null || !speakers.isOpen()) {
                AudioFormat format = new AudioFormat(16000, 16, 1, true, true);
                speakers = AudioSystem.getSourceDataLine(format);
                speakers.open(format);
                speakers.start();
            }
            speakers.write(chunk, 0, chunk.length);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Client::new);
    }
}
