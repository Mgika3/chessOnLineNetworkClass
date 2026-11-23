import reseau.GameConnection;
import reseau.LobbyConnection;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.DefaultListCellRenderer;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.Border;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public final class Hub extends JFrame {
    private static final Color FOND = new Color(14, 20, 31);
    private static final Color PANNEAU = new Color(24, 33, 47);
    private static final Color CARTE = new Color(31, 42, 59);
    private static final Color TEXTE = new Color(239, 244, 250);
    private static final Color SECONDAIRE = new Color(157, 172, 190);
    private static final Color ACCENT = new Color(90, 211, 166);
    private static final Color BORDURE = new Color(53, 68, 88);

    private final JTextField pseudo = new JTextField("Joueur");
    private final JTextField adresseServeur = new JTextField("localhost");
    private final JTextField portServeur = new JTextField("4444");
    private final JButton boutonConnexion = new JButton("Entrer dans le salon");
    private final JButton boutonDefi = new JButton("Envoyer un défi");
    private final JLabel etatConnexion = new JLabel("Hors ligne");
    private final JLabel joueurSelectionne = new JLabel("Sélectionnez un joueur disponible");
    private final DefaultListModel<JoueurEnLigne> modeleJoueurs = new DefaultListModel<>();
    private final JList<JoueurEnLigne> listeJoueurs = new JList<>(modeleJoueurs);
    private final JRadioButton dames = new JRadioButton("Dames");
    private final JRadioButton echecs = new JRadioButton("Échecs");
    private final JRadioButton classique = new JRadioButton("Classique");
    private final JRadioButton noel = new JRadioButton("Noël");
    private LobbyConnection salon;
    private String adresseConnectee;
    private int portConnecte;

    private Hub(String adresse, int port) {
        super("Le Salon | Jeux de plateau");
        adresseServeur.setText(adresse);
        portServeur.setText(Integer.toString(port));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1200, 900));
        setContentPane(creerInterface());
        pack();
        setSize(1100, 740);
        setLocationRelativeTo(null);
        ajouterEcouteurs();
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (salon != null) {
                    salon.close();
                }
            }
        });
    }

    private JPanel creerInterface() {
        JPanel racine = new JPanel(new BorderLayout(0, 20));
        racine.setBackground(FOND);
        racine.setBorder(BorderFactory.createEmptyBorder(28, 34, 24, 34));
        racine.add(creerEntete(), BorderLayout.NORTH);
        racine.add(creerCorps(), BorderLayout.CENTER);

        JLabel pied = new JLabel("Un salon partagé. Des duels séparés. Votre apparence reste la vôtre.");
        pied.setForeground(SECONDAIRE);
        pied.setFont(police(Font.PLAIN, 12));
        racine.add(pied, BorderLayout.SOUTH);
        return racine;
    }

    private JPanel creerEntete() {
        JPanel entete = new JPanel(new BorderLayout(20, 0));
        entete.setOpaque(false);
        JPanel titres = new JPanel();
        titres.setOpaque(false);
        titres.setLayout(new BoxLayout(titres, BoxLayout.Y_AXIS));
        JLabel surtitre = new JLabel("JEUX DE PLATEAU  /  SALON MULTIJOUEUR");
        surtitre.setForeground(ACCENT);
        surtitre.setFont(police(Font.BOLD, 11));
        JLabel titre = new JLabel("Le Salon");
        titre.setForeground(TEXTE);
        titre.setFont(police(Font.BOLD, 34));
        JLabel sousTitre = new JLabel("Retrouvez vos amis et lancez plusieurs duels en parallèle.");
        sousTitre.setForeground(SECONDAIRE);
        sousTitre.setFont(police(Font.PLAIN, 14));
        titres.add(surtitre);
        titres.add(Box.createVerticalStrut(5));
        titres.add(titre);
        titres.add(Box.createVerticalStrut(4));
        titres.add(sousTitre);
        entete.add(titres, BorderLayout.CENTER);

        etatConnexion.setForeground(SECONDAIRE);
        etatConnexion.setFont(police(Font.BOLD, 12));
        etatConnexion.setHorizontalAlignment(SwingConstants.CENTER);
        etatConnexion.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDURE),
                BorderFactory.createEmptyBorder(9, 14, 9, 14)));
        entete.add(etatConnexion, BorderLayout.EAST);
        return entete;
    }

    private JPanel creerCorps() {
        JPanel corps = new JPanel(new BorderLayout(0, 16));
        corps.setOpaque(false);
        corps.add(creerConnexion(), BorderLayout.NORTH);
        JPanel contenu = new JPanel(new GridLayout(1, 2, 18, 0));
        contenu.setOpaque(false);
        contenu.add(creerListeJoueurs());
        contenu.add(creerReglages());
        corps.add(contenu, BorderLayout.CENTER);
        return corps;
    }

    private JPanel creerConnexion() {
        JPanel carte = creerCarte();
        carte.setLayout(new BorderLayout(12, 0));
        JPanel champs = new JPanel(new GridLayout(2, 4, 10, 4));
        champs.setOpaque(false);
        ajouterEtiquette(champs, "VOTRE PSEUDO");
        ajouterEtiquette(champs, "ADRESSE DU SERVEUR");
        ajouterEtiquette(champs, "PORT");
        champs.add(new JLabel());
        styleChamp(pseudo);
        styleChamp(adresseServeur);
        styleChamp(portServeur);
        champs.add(pseudo);
        champs.add(adresseServeur);
        champs.add(portServeur);
        champs.add(boutonConnexion);
        boutonConnexion.setPreferredSize(new Dimension(210, 42));
        styleBoutonPrimaire(boutonConnexion);
        carte.add(champs, BorderLayout.CENTER);
        return carte;
    }

    private JPanel creerListeJoueurs() {
        JPanel carte = creerCarte();
        carte.setLayout(new BorderLayout(0, 12));
        JPanel titre = new JPanel(new BorderLayout(0, 5));
        titre.setOpaque(false);
        JLabel enLigne = new JLabel("Joueurs en ligne");
        enLigne.setForeground(TEXTE);
        enLigne.setFont(police(Font.BOLD, 20));
        JLabel aide = new JLabel("Choisissez une personne disponible pour lui proposer un duel.");
        aide.setForeground(SECONDAIRE);
        aide.setFont(police(Font.PLAIN, 12));
        titre.add(enLigne, BorderLayout.NORTH);
        titre.add(aide, BorderLayout.SOUTH);
        carte.add(titre, BorderLayout.NORTH);

        listeJoueurs.setBackground(PANNEAU);
        listeJoueurs.setForeground(TEXTE);
        listeJoueurs.setSelectionBackground(new Color(47, 67, 83));
        listeJoueurs.setSelectionForeground(TEXTE);
        listeJoueurs.setFixedCellHeight(64);
        listeJoueurs.setCellRenderer(new RenduJoueur());
        JScrollPane defilement = new JScrollPane(listeJoueurs);
        defilement.setBorder(BorderFactory.createLineBorder(BORDURE));
        defilement.getViewport().setBackground(PANNEAU);
        carte.add(defilement, BorderLayout.CENTER);

        joueurSelectionne.setForeground(SECONDAIRE);
        joueurSelectionne.setFont(police(Font.PLAIN, 12));
        carte.add(joueurSelectionne, BorderLayout.SOUTH);
        return carte;
    }

    private JPanel creerReglages() {
        JPanel reglage = new JPanel();
        reglage.setOpaque(false);
        reglage.setLayout(new BoxLayout(reglage, BoxLayout.Y_AXIS));

        JPanel choixJeu = creerCarte();
        choixJeu.setLayout(new BorderLayout(0, 13));
        choixJeu.add(titreSection("01", "Choisissez votre jeu"), BorderLayout.NORTH);
        ButtonGroup groupeJeux = new ButtonGroup();
        groupeJeux.add(dames);
        groupeJeux.add(echecs);
        dames.setSelected(true);
        JPanel jeux = new JPanel(new GridLayout(1, 2, 10, 0));
        jeux.setOpaque(false);
        jeux.add(creerOption(dames, "10 × 10", "Captures en chaîne"));
        jeux.add(creerOption(echecs, "8 × 8", "Stratégie classique"));
        choixJeu.add(jeux, BorderLayout.CENTER);
        reglage.add(choixJeu);
        reglage.add(Box.createVerticalStrut(14));

        JPanel choixSkin = creerCarte();
        choixSkin.setLayout(new BorderLayout(0, 13));
        choixSkin.add(titreSection("02", "Votre apparence"), BorderLayout.NORTH);
        ButtonGroup groupeSkins = new ButtonGroup();
        groupeSkins.add(classique);
        groupeSkins.add(noel);
        classique.setSelected(true);
        JPanel skins = new JPanel(new GridLayout(1, 2, 10, 0));
        skins.setOpaque(false);
        skins.add(creerOption(classique, "Classique", "Bois chaud et sobre"));
        skins.add(creerOption(noel, "Hiver", "Neige et couleurs festives"));
        choixSkin.add(skins, BorderLayout.CENTER);
        JLabel noteSkin = new JLabel("Ce choix ne modifie que votre propre plateau.");
        noteSkin.setForeground(SECONDAIRE);
        noteSkin.setFont(police(Font.PLAIN, 11));
        choixSkin.add(noteSkin, BorderLayout.SOUTH);
        reglage.add(choixSkin);
        reglage.add(Box.createVerticalStrut(14));

        JPanel actions = creerCarte();
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        joueurSelectionne.setAlignmentX(Component.LEFT_ALIGNMENT);
        actions.add(titreSection("03", "Lancer une partie"));
        actions.add(Box.createVerticalStrut(12));
        boutonDefi.setAlignmentX(Component.LEFT_ALIGNMENT);
        boutonDefi.setMaximumSize(new Dimension(Integer.MAX_VALUE, 48));
        styleBoutonPrimaire(boutonDefi);
        actions.add(boutonDefi);
        actions.add(Box.createVerticalStrut(9));
        JButton partieLocale = new JButton("Jouer en local sur cet appareil");
        partieLocale.setAlignmentX(Component.LEFT_ALIGNMENT);
        partieLocale.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
        styleBoutonSecondaire(partieLocale);
        partieLocale.addActionListener(event -> lancerPartieLocale());
        actions.add(partieLocale);
        reglage.add(actions);
        reglage.add(Box.createVerticalGlue());
        return reglage;
    }

    private JPanel creerOption(JRadioButton choix, String titre, String description) {
        JPanel option = new JPanel(new BorderLayout(0, 6));
        option.setBackground(CARTE);
        Border bordure = BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDURE),
                BorderFactory.createEmptyBorder(10, 11, 10, 11));
        option.setBorder(bordure);
        choix.setForeground(TEXTE);
        choix.setFont(police(Font.BOLD, 14));
        choix.setOpaque(false);
        choix.setFocusPainted(false);
        option.add(choix, BorderLayout.NORTH);
        JLabel descriptionLabel = new JLabel(description);
        descriptionLabel.setForeground(SECONDAIRE);
        descriptionLabel.setFont(police(Font.PLAIN, 11));
        option.add(descriptionLabel, BorderLayout.CENTER);
        JLabel nom = new JLabel(titre);
        nom.setForeground(ACCENT);
        nom.setFont(police(Font.BOLD, 11));
        option.add(nom, BorderLayout.SOUTH);
        choix.addItemListener(event -> option.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(choix.isSelected() ? ACCENT : BORDURE,
                        choix.isSelected() ? 2 : 1),
                BorderFactory.createEmptyBorder(9, 10, 9, 10))));
        option.setPreferredSize(new Dimension(180, 96));
        return option;
    }

    private JPanel creerCarte() {
        JPanel carte = new JPanel();
        carte.setBackground(PANNEAU);
        carte.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDURE),
                BorderFactory.createEmptyBorder(17, 18, 17, 18)));
        return carte;
    }

    private JPanel titreSection(String numero, String titre) {
        JPanel ligne = new JPanel(new BorderLayout(9, 0));
        ligne.setOpaque(false);
        JLabel index = new JLabel(numero);
        index.setForeground(ACCENT);
        index.setFont(police(Font.BOLD, 12));
        JLabel texte = new JLabel(titre);
        texte.setForeground(TEXTE);
        texte.setFont(police(Font.BOLD, 17));
        ligne.add(index, BorderLayout.WEST);
        ligne.add(texte, BorderLayout.CENTER);
        return ligne;
    }

    private void ajouterEtiquette(JPanel parent, String texte) {
        JLabel etiquette = new JLabel(texte);
        etiquette.setForeground(SECONDAIRE);
        etiquette.setFont(police(Font.BOLD, 10));
        parent.add(etiquette);
    }

    private void styleChamp(JTextField champ) {
        champ.setForeground(TEXTE);
        champ.setBackground(new Color(17, 25, 37));
        champ.setCaretColor(ACCENT);
        champ.setFont(police(Font.PLAIN, 13));
        champ.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDURE),
                BorderFactory.createEmptyBorder(8, 10, 8, 10)));
    }

    private void styleBoutonPrimaire(JButton bouton) {
        bouton.setForeground(new Color(13, 37, 31));
        bouton.setBackground(ACCENT);
        bouton.setFont(police(Font.BOLD, 13));
        bouton.setFocusPainted(false);
        bouton.setBorder(BorderFactory.createEmptyBorder(11, 16, 11, 16));
        bouton.setCursor(java.awt.Cursor.getPredefinedCursor(java.awt.Cursor.HAND_CURSOR));
    }

    private void styleBoutonSecondaire(JButton bouton) {
        bouton.setForeground(TEXTE);
        bouton.setBackground(CARTE);
        bouton.setFont(police(Font.BOLD, 12));
        bouton.setFocusPainted(false);
        bouton.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDURE),
                BorderFactory.createEmptyBorder(8, 12, 8, 12)));
    }

    private void ajouterEcouteurs() {
        boutonConnexion.addActionListener(event -> {
            if (salon == null) {
                connecterAuSalon();
            } else {
                deconnecterDuSalon();
            }
        });
        listeJoueurs.addListSelectionListener(event -> actualiserSelection());
        boutonDefi.addActionListener(event -> envoyerDefi());
        actualiserSelection();
    }

    private void connecterAuSalon() {
        String nom = pseudo.getText().trim();
        String adresse = adresseServeur.getText().trim();
        final int port;
        try {
            port = Integer.parseInt(portServeur.getText().trim());
            if (port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            afficherErreur("Le port doit être compris entre 1 et 65535.");
            return;
        }
        if (adresse.isEmpty()) {
            afficherErreur("Saisissez l’adresse du serveur.");
            return;
        }
        if (!nom.matches("[\\p{L}\\p{N}_-]{2,18}")) {
            afficherErreur("Le pseudo doit contenir 2 à 18 lettres, chiffres, tirets ou soulignés.");
            return;
        }

        boutonConnexion.setEnabled(false);
        boutonConnexion.setText("Connexion...");
        etatConnexion.setText("Connexion...");
        new SwingWorker<LobbyConnection, Void>() {
            @Override
            protected LobbyConnection doInBackground() throws Exception {
                return LobbyConnection.connect(adresse, port, nom);
            }

            @Override
            protected void done() {
                try {
                    salon = get();
                    adresseConnectee = adresse;
                    portConnecte = port;
                    salon.definirEcouteur(message -> SwingUtilities.invokeLater(
                            () -> traiterMessageSalon(message)));
                    salon.envoyer("LIST");
                    pseudo.setEditable(false);
                    adresseServeur.setEditable(false);
                    portServeur.setEditable(false);
                    boutonConnexion.setEnabled(true);
                    boutonConnexion.setText("Quitter le salon");
                    etatConnexion.setText("Connecté · " + nom);
                    etatConnexion.setForeground(ACCENT);
                } catch (Exception exception) {
                    if (salon != null) {
                        salon.close();
                    }
                    salon = null;
                    boutonConnexion.setEnabled(true);
                    boutonConnexion.setText("Entrer dans le salon");
                    etatConnexion.setText("Hors ligne");
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    afficherErreur("Connexion impossible : " + cause.getMessage());
                }
            }
        }.execute();
    }

    private void deconnecterDuSalon() {
        if (salon != null) {
            salon.close();
            salon = null;
        }
        modeleJoueurs.clear();
        pseudo.setEditable(true);
        adresseServeur.setEditable(true);
        portServeur.setEditable(true);
        boutonConnexion.setText("Entrer dans le salon");
        etatConnexion.setText("Hors ligne");
        etatConnexion.setForeground(SECONDAIRE);
        actualiserSelection();
    }

    private void traiterMessageSalon(String message) {
        String[] champs = message.split("\\|", -1);
        try {
            switch (champs[0]) {
                case "PLAYERS":
                    actualiserJoueurs(champs.length > 1 ? champs[1] : "");
                    break;
                case "CHALLENGE":
                    recevoirDefi(champs);
                    break;
                case "MATCH":
                    if (champs.length == 5) {
                        rejoindrePartie(champs);
                    } else {
                        afficherErreur("Le serveur a envoyé une configuration de partie invalide.");
                    }
                    break;
                case "CHALLENGE_SENT":
                    if (champs.length == 3) {
                        joueurSelectionne.setText("Défi envoyé à " + LobbyConnection.decoder(champs[2])
                                + " · en attente de réponse");
                        joueurSelectionne.setForeground(ACCENT);
                    }
                    break;
                case "DECLINED":
                    if (champs.length == 3) {
                        JOptionPane.showMessageDialog(this,
                                LobbyConnection.decoder(champs[2]) + " a décliné le défi.",
                                "Défi décliné", JOptionPane.INFORMATION_MESSAGE);
                    }
                    break;
                case "CHALLENGE_CANCELLED":
                    if (champs.length == 3) {
                        JOptionPane.showMessageDialog(this,
                                "Le défi avec " + LobbyConnection.decoder(champs[2])
                                        + " a été annulé car ce joueur s’est déconnecté.",
                                "Défi annulé", JOptionPane.INFORMATION_MESSAGE);
                    }
                    break;
                case "MATCH_END":
                    setVisible(true);
                    toFront();
                    etatConnexion.setText("Connecté · partie terminée");
                    break;
                case "ERROR":
                    if (champs.length > 1) {
                        afficherErreur(LobbyConnection.decoder(champs[1]));
                    }
                    break;
                case "DISCONNECTED":
                    modeleJoueurs.clear();
                    salon = null;
                    pseudo.setEditable(true);
                    adresseServeur.setEditable(true);
                    portServeur.setEditable(true);
                    boutonConnexion.setEnabled(true);
                    boutonConnexion.setText("Entrer dans le salon");
                    etatConnexion.setText("Connexion interrompue");
                    etatConnexion.setForeground(new Color(240, 150, 135));
                    if (champs.length > 1 && !champs[1].isEmpty()) {
                        JOptionPane.showMessageDialog(this, champs[1],
                                "Connexion interrompue", JOptionPane.WARNING_MESSAGE);
                    }
                    break;
                default:
                    afficherErreur("Message de salon inconnu : " + champs[0]);
            }
        } catch (IOException | ArrayIndexOutOfBoundsException exception) {
            afficherErreur("Impossible de lire la réponse du salon : " + exception.getMessage());
        }
    }

    private void actualiserJoueurs(String contenu) throws IOException {
        String selectionPrecedente = listeJoueurs.getSelectedValue() == null
                ? null : listeJoueurs.getSelectedValue().pseudo;
        List<JoueurEnLigne> joueurs = new ArrayList<>();
        if (!contenu.isEmpty()) {
            for (String entree : contenu.split(",")) {
                String[] champs = entree.split(":", -1);
                if (champs.length == 2) {
                    joueurs.add(new JoueurEnLigne(LobbyConnection.decoder(champs[0]),
                            "1".equals(champs[1])));
                }
            }
        }
        modeleJoueurs.clear();
        for (JoueurEnLigne joueur : joueurs) {
            modeleJoueurs.addElement(joueur);
            if (joueur.pseudo.equals(selectionPrecedente)) {
                listeJoueurs.setSelectedValue(joueur, true);
            }
        }
        if (joueurs.isEmpty()) {
            listeJoueurs.clearSelection();
        }
        actualiserSelection();
    }

    private void recevoirDefi(String[] champs) throws IOException {
        if (champs.length != 4) {
            throw new IOException("Invitation incomplète.");
        }
        String invitant = LobbyConnection.decoder(champs[2]);
        String nomJeu = "chess".equals(champs[3]) ? "échecs" : "dames";
        int reponse = JOptionPane.showConfirmDialog(this,
                invitant + " vous propose un duel de " + nomJeu + ".\n"
                        + "Accepter démarre la partie dans une fenêtre séparée.",
                "Nouvelle demande de duel", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        envoyerCommande((reponse == JOptionPane.YES_OPTION ? "ACCEPT|" : "DECLINE|") + champs[1]);
    }

    private void rejoindrePartie(String[] champs) throws IOException {
        String token = champs[1];
        String jeu = champs[2];
        boolean joueurBlanc = "WHITE".equals(champs[3]);
        String adversaire = LobbyConnection.decoder(champs[4]);
        if ((!joueurBlanc && !"BLACK".equals(champs[3]))
                || (!"chess".equals(jeu) && !"checkers".equals(jeu))) {
            throw new IOException("Le serveur a envoyé des paramètres de partie invalides.");
        }
        boolean skinLocal = noel.isSelected();
        etatConnexion.setText("Préparation du duel contre " + adversaire + "…");
        new SwingWorker<GameConnection, Void>() {
            @Override
            protected GameConnection doInBackground() throws Exception {
                return GameConnection.joinHub(adresseConnectee, portConnecte, token,
                        jeu, skinLocal, joueurBlanc);
            }

            @Override
            protected void done() {
                try {
                    GameConnection connexion = get();
                    setVisible(false);
                    ouvrirJeu(jeu, skinLocal, connexion, joueurBlanc);
                } catch (Exception exception) {
                    Throwable cause = exception.getCause() == null ? exception : exception.getCause();
                    afficherErreur("Impossible de rejoindre la partie : " + cause.getMessage());
                    setVisible(true);
                }
            }
        }.execute();
    }

    private void envoyerDefi() {
        JoueurEnLigne cible = listeJoueurs.getSelectedValue();
        if (salon == null) {
            afficherErreur("Connectez-vous au salon avant d’envoyer un défi.");
            return;
        }
        if (cible == null || cible.occupe) {
            afficherErreur("Sélectionnez un joueur disponible.");
            return;
        }
        String jeu = echecs.isSelected() ? "chess" : "checkers";
        envoyerCommande("CHALLENGE|" + LobbyConnection.encoder(cible.pseudo) + "|" + jeu);
    }

    private void lancerPartieLocale() {
        boolean modeNoel = noel.isSelected();
        String jeu = echecs.isSelected() ? "chess" : "checkers";
        setVisible(false);
        ouvrirJeu(jeu, modeNoel, null, true);
    }

    private void ouvrirJeu(String jeu, boolean modeNoel,
                           GameConnection connexion, boolean joueurBlanc) {
        Runnable retourAuHub = () -> {
            setVisible(true);
            toFront();
        };
        if ("chess".equals(jeu)) {
            new jeuEchec.Main(modeNoel, retourAuHub, connexion, joueurBlanc);
        } else {
            new jeuDeDame.Main(modeNoel, retourAuHub, connexion, joueurBlanc);
        }
    }

    private void envoyerCommande(String commande) {
        if (salon == null) {
            afficherErreur("Vous n’êtes plus connecté au salon.");
            return;
        }
        try {
            salon.envoyer(commande);
        } catch (IOException exception) {
            afficherErreur(exception.getMessage());
        }
    }

    private void actualiserSelection() {
        JoueurEnLigne joueur = listeJoueurs.getSelectedValue();
        boolean connecte = salon != null;
        boolean disponible = joueur != null && !joueur.occupe;
        boutonDefi.setEnabled(connecte && disponible);
        if (joueur == null) {
            joueurSelectionne.setText(connecte
                    ? "Sélectionnez un joueur disponible" : "Connectez-vous pour voir les joueurs");
            joueurSelectionne.setForeground(SECONDAIRE);
        } else if (joueur.occupe) {
            joueurSelectionne.setText(joueur.pseudo + " est déjà engagé dans un défi ou une partie.");
            joueurSelectionne.setForeground(SECONDAIRE);
        } else {
            joueurSelectionne.setText("Défi sélectionné : " + joueur.pseudo);
            joueurSelectionne.setForeground(ACCENT);
        }
    }

    private void afficherErreur(String message) {
        JOptionPane.showMessageDialog(this, message, "Salon des jeux", JOptionPane.ERROR_MESSAGE);
    }

    private static Font police(int style, int taille) {
        return new Font(Font.SANS_SERIF, style, taille);
    }

    public static void main(String[] args) {
        if (args.length > 0 && "server".equalsIgnoreCase(args[0])) {
            Serveur.main(args.length > 1 ? new String[]{args[1]} : new String[0]);
        } else {
            String adresse = args.length > 1 ? args[1] : "localhost";
            int port = 4444;
            if (args.length > 2) {
                try {
                    port = Integer.parseInt(args[2]);
                } catch (NumberFormatException exception) {
                    System.err.println("Port invalide : indiquez une valeur entre 1 et 65535.");
                    return;
                }
            }
            demarrerReseau(adresse, Integer.toString(port));
        }
    }

    static void demarrerReseau(String adresse, String port) {
        final int portValeur;
        try {
            portValeur = Integer.parseInt(port);
            if (portValeur < 1 || portValeur > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException exception) {
            System.err.println("Port invalide : indiquez une valeur entre 1 et 65535.");
            return;
        }
        SwingUtilities.invokeLater(() -> new Hub(adresse, portValeur).setVisible(true));
    }

    private static final class JoueurEnLigne {
        private final String pseudo;
        private final boolean occupe;

        private JoueurEnLigne(String pseudo, boolean occupe) {
            this.pseudo = pseudo;
            this.occupe = occupe;
        }

        @Override
        public String toString() {
            return pseudo;
        }
    }

    private static final class RenduJoueur extends DefaultListCellRenderer {
        @Override
        public Component getListCellRendererComponent(JList<?> liste, Object valeur, int index,
                                                      boolean selectionne, boolean focus) {
            super.getListCellRendererComponent(liste, valeur, index, selectionne, focus);
            JoueurEnLigne joueur = (JoueurEnLigne) valeur;
            setText("●  " + joueur.pseudo
                    + (joueur.occupe ? "     Occupé" : "     Disponible"));
            setForeground(joueur.occupe ? SECONDAIRE : ACCENT);
            setFont(police(Font.BOLD, 13));
            setBorder(BorderFactory.createEmptyBorder(0, 13, 0, 13));
            if (selectionne) {
                setBackground(new Color(47, 67, 83));
                setForeground(TEXTE);
            } else {
                setBackground(PANNEAU);
            }
            return this;
        }
    }
}
