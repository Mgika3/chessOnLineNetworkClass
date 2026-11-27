package jeuDeDame;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.Timer;
import javax.swing.SwingUtilities;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.io.IOException;
import reseau.GameConnection;

/**
 * Plateau international 10x10 et moteur des règles de dames.
 *
 * Le moteur recherche tous les chemins de prise possibles. Si des prises existent,
 * seules celles ayant le plus grand nombre de pièces capturées restent autorisées.
 * Dans une même prise, cheminsLegaux représente les coups courts disponibles tandis
 * que cheminsRestants conserve les destinations possibles au milieu d'une rafle.
 */
public class JeuDeDame extends JPanel {
    private static final int NOMBRE_CASES = 10;
    private static final int BLANC = Piece.BLANC;
    private static final int NOIR = Piece.NOIR;
    private static final int[][] DIRECTIONS = {{-1, -1}, {-1, 1}, {1, -1}, {1, 1}};
    private static final Color CASE_CLAIRE = new Color(238, 219, 180);
    private static final Color CASE_FONCEE = new Color(105, 65, 43);
    private static final Color NEIGE = new Color(237, 244, 249);
    private static final Color BLEU_HIVER = new Color(83, 108, 132);
    private static final Color SAPIN = new Color(39, 78, 59);
    private static final Color CREME = new Color(255, 242, 210);
    private static final Color CANNEBERGE = new Color(163, 40, 55);
    private static final Color DEPLACEMENT = new Color(210, 210, 210, 190);

    private final boolean modeNoel;
    private final Piece[][] plateau = new Piece[NOMBRE_CASES][NOMBRE_CASES];
    private final JLabel statut = new JLabel("", SwingConstants.LEFT);
    private final JLabel horloge = new JLabel("Temps : 00:00:00", SwingConstants.RIGHT);
    private final PanneauPlateau panneauPlateau = new PanneauPlateau();
    private final long debutPartie = System.currentTimeMillis();
    private final Timer timer;
    private final GameConnection connexion;
    private final int couleurLocale;

    private int joueur = BLANC;
    private int ligneSelectionnee = -1;
    private int colonneSelectionnee = -1;
    private List<Chemin> cheminsLegaux = Collections.emptyList();
    private List<Chemin> cheminsRestants = Collections.emptyList();
    private boolean priseEnCours;
    private boolean partieTerminee;

    public JeuDeDame() {
        this(false);
    }

    public JeuDeDame(boolean modeNoel) {
        this(modeNoel, null, true);
    }

    public JeuDeDame(boolean modeNoel, GameConnection connexion, boolean joueurBlanc) {
        this.modeNoel = modeNoel;
        this.connexion = connexion;
        couleurLocale = joueurBlanc ? BLANC : NOIR;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));

        JPanel barreEtat = new JPanel(new BorderLayout());
        barreEtat.add(statut, BorderLayout.CENTER);
        barreEtat.add(horloge, BorderLayout.EAST);
        add(barreEtat, BorderLayout.NORTH);
        add(panneauPlateau, BorderLayout.CENTER);

        initialiserPlateau();
        actualiserCheminsLegaux();
        actualiserStatut();

        timer = new Timer(1000, event -> actualiserHorloge());
        timer.start();
        if (connexion != null) {
            connexion.definirEcouteurCoups(coup -> SwingUtilities.invokeLater(
                    () -> appliquerCoupDistant(coup)));
        }
    }

    public void arreterHorloge() {
        timer.stop();
    }

    private void initialiserPlateau() {
        for (int ligne = 0; ligne < 4; ligne++) {
            for (int colonne = 0; colonne < NOMBRE_CASES; colonne++) {
                if ((ligne + colonne) % 2 != 0) {
                    plateau[ligne][colonne] = new Piece(NOIR); // Les quatre premières rangées des Noirs.
                }
            }
        }
        for (int ligne = 6; ligne < NOMBRE_CASES; ligne++) {
            for (int colonne = 0; colonne < NOMBRE_CASES; colonne++) {
                if ((ligne + colonne) % 2 != 0) {
                    plateau[ligne][colonne] = new Piece(BLANC);
                }
            }
        }
    }

    private void actualiserHorloge() {
        long secondes = (System.currentTimeMillis() - debutPartie) / 1000;
        long minutes = secondes / 60;
        horloge.setText(String.format("Temps : %02d:%02d:%02d", minutes / 60, minutes % 60, secondes % 60));
    }

    private void actualiserStatut() {
        if (partieTerminee) {
            return;
        }
        String nomJoueur = joueur == BLANC ? "Blancs" : "Noirs";
        boolean priseObligatoire = !cheminsLegaux.isEmpty() && cheminsLegaux.get(0).prise;
        String instruction = ligneSelectionnee < 0
                ? " — sélectionnez un pion"
                : " — choisissez une case grisée";
        statut.setText("Au tour des " + nomJoueur
                + (priseObligatoire ? " (prise obligatoire)" : "") + instruction
                + (connexion == null ? "" : joueur == couleurLocale
                ? " — à vous" : " — tour adverse"));
    }

    private void actualiserCheminsLegaux() {
        /*
         * Une partie où une prise est possible doit obligatoirement en réaliser une.
         * Cette recherche calcule d'abord toutes les prises puis conserve seulement celles
         * ayant la longueur maximale : c'est la règle de la prise majoritaire utilisée ici.
         */
        List<Chemin> prises = new ArrayList<>();
        for (int ligne = 0; ligne < NOMBRE_CASES; ligne++) {
            for (int colonne = 0; colonne < NOMBRE_CASES; colonne++) {
                Piece piece = plateau[ligne][colonne];
                if (piece != null && piece.getCouleur() == joueur) {
                    prises.addAll(calculerPrises(plateau, ligne, colonne, piece.getCouleur()));
                }
            }
        }

        if (!prises.isEmpty()) {
            int longueurMax = prises.stream().mapToInt(chemin -> chemin.destinations.size()).max().orElse(0);
            cheminsLegaux = new ArrayList<>();
            for (Chemin chemin : prises) {
                if (chemin.destinations.size() == longueurMax) {
                    cheminsLegaux.add(chemin);
                }
            }
            return;
        }

        List<Chemin> deplacements = new ArrayList<>();
        for (int ligne = 0; ligne < NOMBRE_CASES; ligne++) {
            for (int colonne = 0; colonne < NOMBRE_CASES; colonne++) {
                Piece piece = plateau[ligne][colonne];
                if (piece != null && piece.getCouleur() == joueur) {
                    for (int[] destination : destinationsSimples(plateau, ligne, colonne)) {
                        deplacements.add(new Chemin(ligne, colonne,
                                Collections.singletonList(new Position(destination[0], destination[1])), false));
                    }
                }
            }
        }
        cheminsLegaux = deplacements;
    }

    private List<Chemin> calculerPrises(Piece[][] etat, int ligne, int colonne, int couleur) {
        // La méthode explore récursivement chaque branche de prise. Une copie du plateau
        // permet de retirer le pion capturé puis de tester les sauts suivants indépendamment.
        List<Chemin> resultats = new ArrayList<>();
        Piece piece = etat[ligne][colonne];
        for (Position destination : destinationsDePrise(etat, ligne, colonne)) {
            Piece[][] nouvelEtat = copierPlateau(etat);
            appliquerDeplacement(nouvelEtat, ligne, colonne, destination.ligne, destination.colonne);
            Piece deplacee = nouvelEtat[destination.ligne][destination.colonne];
            boolean vientDePromouvoir = !piece.estDame() && deplacee.estDame()
                    && estLigneDePromotion(destination.ligne, couleur);
            if (vientDePromouvoir) {
                resultats.add(new Chemin(ligne, colonne,
                        Collections.singletonList(destination), true));
                continue;
            }

            List<Chemin> suites = calculerPrises(nouvelEtat, destination.ligne, destination.colonne, couleur);
            if (suites.isEmpty()) {
                resultats.add(new Chemin(ligne, colonne,
                        Collections.singletonList(destination), true));
            } else {
                for (Chemin suite : suites) {
                    List<Position> cheminComplet = new ArrayList<>();
                    cheminComplet.add(destination);
                    cheminComplet.addAll(suite.destinations);
                    resultats.add(new Chemin(ligne, colonne, cheminComplet, true));
                }
            }
        }
        return resultats;
    }

    private List<Position> destinationsDePrise(Piece[][] etat, int ligne, int colonne) {
        Piece piece = etat[ligne][colonne];
        List<Position> destinations = new ArrayList<>();
        for (int[] direction : DIRECTIONS) {
            int pasLigne = direction[0];
            int pasColonne = direction[1];
            int ligneSuivante = ligne + pasLigne;
            int colonneSuivante = colonne + pasColonne;
            if (!piece.estDame()) {
                int ligneArrivee = ligne + 2 * pasLigne;
                int colonneArrivee = colonne + 2 * pasColonne;
                if (dansPlateau(ligneArrivee, colonneArrivee)
                        && etat[ligneSuivante][colonneSuivante] != null
                        && etat[ligneSuivante][colonneSuivante].getCouleur() != piece.getCouleur()
                        && etat[ligneArrivee][colonneArrivee] == null) {
                    destinations.add(new Position(ligneArrivee, colonneArrivee));
                }
                continue;
            }

            while (dansPlateau(ligneSuivante, colonneSuivante)
                    && etat[ligneSuivante][colonneSuivante] == null) {
                ligneSuivante += pasLigne;
                colonneSuivante += pasColonne;
            }
            if (!dansPlateau(ligneSuivante, colonneSuivante)
                    || etat[ligneSuivante][colonneSuivante].getCouleur() == piece.getCouleur()) {
                continue;
            }
            ligneSuivante += pasLigne;
            colonneSuivante += pasColonne;
            while (dansPlateau(ligneSuivante, colonneSuivante)
                    && etat[ligneSuivante][colonneSuivante] == null) {
                destinations.add(new Position(ligneSuivante, colonneSuivante));
                ligneSuivante += pasLigne;
                colonneSuivante += pasColonne;
            }
        }
        return destinations;
    }

    private List<int[]> destinationsSimples(Piece[][] etat, int ligne, int colonne) {
        Piece piece = etat[ligne][colonne];
        List<int[]> destinations = new ArrayList<>();
        for (int[] direction : DIRECTIONS) {
            if (!piece.estDame() && direction[0] != (piece.getCouleur() == BLANC ? -1 : 1)) {
                continue;
            }
            int ligneSuivante = ligne + direction[0];
            int colonneSuivante = colonne + direction[1];
            while (dansPlateau(ligneSuivante, colonneSuivante)
                    && etat[ligneSuivante][colonneSuivante] == null) {
                destinations.add(new int[]{ligneSuivante, colonneSuivante});
                if (!piece.estDame()) {
                    break;
                }
                ligneSuivante += direction[0];
                colonneSuivante += direction[1];
            }
        }
        return destinations;
    }

    private void gererClic(int x, int y) {
        if (partieTerminee) {
            return;
        }
        if (connexion != null && joueur != couleurLocale) {
            return;
        }
        int taille = Math.min(panneauPlateau.getWidth(), panneauPlateau.getHeight());
        int tailleCase = taille / NOMBRE_CASES;
        if (tailleCase == 0) {
            return;
        }
        int xDepart = (panneauPlateau.getWidth() - taille) / 2;
        int yDepart = (panneauPlateau.getHeight() - taille) / 2;
        int colonne = (x - xDepart) / tailleCase;
        int ligne = (y - yDepart) / tailleCase;
        if (x < xDepart || y < yDepart || !dansPlateau(ligne, colonne)) {
            return;
        }
        if (couleurLocale == NOIR) {
            ligne = NOMBRE_CASES - 1 - ligne;
            colonne = NOMBRE_CASES - 1 - colonne;
        }

        if (ligneSelectionnee >= 0 && estDestinationLegale(ligne, colonne)) {
            jouerDeplacement(ligne, colonne, true);
            return;
        }
        if (priseEnCours) {
            return;
        }

        Piece piece = plateau[ligne][colonne];
        if (piece != null && piece.getCouleur() == joueur && cheminsPour(ligne, colonne).size() > 0) {
            ligneSelectionnee = ligne;
            colonneSelectionnee = colonne;
            cheminsRestants = cheminsPour(ligne, colonne);
        } else if (cheminsRestants.isEmpty() || !cheminsRestants.get(0).prise) {
            annulerSelection();
        }
        actualiserStatut();
        panneauPlateau.repaint();
    }

    private List<Chemin> cheminsPour(int ligne, int colonne) {
        List<Chemin> resultats = new ArrayList<>();
        for (Chemin chemin : cheminsLegaux) {
            if (chemin.ligneDepart == ligne && chemin.colonneDepart == colonne) {
                resultats.add(chemin);
            }
        }
        return resultats;
    }

    private boolean estDestinationLegale(int ligne, int colonne) {
        for (Chemin chemin : cheminsRestants) {
            if (!chemin.destinations.isEmpty()
                    && chemin.destinations.get(0).ligne == ligne
                    && chemin.destinations.get(0).colonne == colonne) {
                return true;
            }
        }
        return false;
    }

    private void jouerDeplacement(int ligneArrivee, int colonneArrivee, boolean transmettre) {
        Chemin cheminReference = cheminsRestants.get(0);
        boolean prise = cheminReference.prise;
        int ligneDepart = ligneSelectionnee;
        int colonneDepart = colonneSelectionnee;
        if (transmettre && connexion != null) {
            try {
                connexion.envoyerCoup(ligneDepart + "|" + colonneDepart + "|"
                        + ligneArrivee + "|" + colonneArrivee);
            } catch (IOException exception) {
                statut.setText("Coup non envoyé : " + exception.getMessage());
                return;
            }
        }
        Piece piece = plateau[ligneDepart][colonneDepart];
        boolean etaitDame = piece.estDame();

        appliquerDeplacement(plateau, ligneDepart, colonneDepart, ligneArrivee, colonneArrivee);
        Piece pieceDeplacee = plateau[ligneArrivee][colonneArrivee];
        boolean promotion = !etaitDame && pieceDeplacee.estDame();

        List<Chemin> suites = new ArrayList<>();
        for (Chemin chemin : cheminsRestants) {
            if (!chemin.destinations.isEmpty()
                    && chemin.destinations.get(0).ligne == ligneArrivee
                    && chemin.destinations.get(0).colonne == colonneArrivee
                    && chemin.destinations.size() > 1) {
                suites.add(chemin.suivant());
            }
        }

        if (prise && !promotion && !suites.isEmpty()) {
            priseEnCours = true;
            ligneSelectionnee = ligneArrivee;
            colonneSelectionnee = colonneArrivee;
            cheminsRestants = suites;
            actualiserStatut();
            panneauPlateau.repaint();
            return;
        }
        terminerTour();
    }

    private void appliquerCoupDistant(String message) {
        // Le traitement distant reprend exactement le même Coup codé que le clic local,
        // mais seulement si le tour distant correspond au joueur adverse du thread Swing.
        if (partieTerminee || connexion == null || joueur == couleurLocale) {
            return;
        }
        String[] champs = message.split("\\|", -1);
        if (champs.length != 4) {
            statut.setText("Coup reçu invalide.");
            return;
        }
        try {
            int ligneDepart = Integer.parseInt(champs[0]);
            int colonneDepart = Integer.parseInt(champs[1]);
            int ligneArrivee = Integer.parseInt(champs[2]);
            int colonneArrivee = Integer.parseInt(champs[3]);
            if (!dansPlateau(ligneDepart, colonneDepart)
                    || !dansPlateau(ligneArrivee, colonneArrivee)) {
                throw new IllegalArgumentException();
            }
            List<Chemin> chemins = priseEnCours
                    ? cheminsRestants : cheminsPour(ligneDepart, colonneDepart);
            if (priseEnCours && (ligneDepart != ligneSelectionnee
                    || colonneDepart != colonneSelectionnee)) {
                throw new IllegalArgumentException();
            }
            boolean destinationValide = chemins.stream().anyMatch(chemin ->
                    !chemin.destinations.isEmpty()
                            && chemin.destinations.get(0).ligne == ligneArrivee
                            && chemin.destinations.get(0).colonne == colonneArrivee);
            if (!destinationValide) {
                throw new IllegalArgumentException();
            }
            ligneSelectionnee = ligneDepart;
            colonneSelectionnee = colonneDepart;
            cheminsRestants = chemins;
            jouerDeplacement(ligneArrivee, colonneArrivee, false);
        } catch (IllegalArgumentException exception) {
            statut.setText("Coup reçu illégal, partie synchronisée interrompue.");
        }
    }

    private void appliquerDeplacement(Piece[][] etat, int ligneDepart, int colonneDepart,
                                     int ligneArrivee, int colonneArrivee) {
        Piece piece = etat[ligneDepart][colonneDepart]; // Pièce déplacée sur le plateau simulé.
        int pasLigne = Integer.compare(ligneArrivee, ligneDepart); // Direction verticale de la dame ou du pion.
        int pasColonne = Integer.compare(colonneArrivee, colonneDepart); // Direction horizontale du déplacement.
        int ligne = ligneDepart + pasLigne; // Première case traversée.
        int colonne = colonneDepart + pasColonne; // Première colonne traversée.
        while (ligne != ligneArrivee && colonne != colonneArrivee) {
            if (etat[ligne][colonne] != null) {
                etat[ligne][colonne] = null; // Supprime l'adversaire rencontré.
                break;
            }
            ligne += pasLigne;
            colonne += pasColonne;
        }
        etat[ligneDepart][colonneDepart] = null;
        etat[ligneArrivee][colonneArrivee] = piece;
        if (!piece.estDame() && estLigneDePromotion(ligneArrivee, piece.getCouleur())) {
            piece.promouvoir();
        }
    }

    private void terminerTour() {
        joueur = joueur == BLANC ? NOIR : BLANC;
        ligneSelectionnee = -1;
        colonneSelectionnee = -1;
        priseEnCours = false;
        cheminsRestants = Collections.emptyList();
        actualiserCheminsLegaux();
        if (cheminsLegaux.isEmpty()) {
            partieTerminee = true;
            statut.setText("Victoire des " + (joueur == BLANC ? "Noirs" : "Blancs") + " !");
            timer.stop();
        } else {
            actualiserStatut();
        }
        panneauPlateau.repaint();
    }

    private void annulerSelection() {
        ligneSelectionnee = -1;
        colonneSelectionnee = -1;
        priseEnCours = false;
        cheminsRestants = Collections.emptyList();
    }

    private List<Position> destinationsSelectionnees() {
        List<Position> destinations = new ArrayList<>();
        for (Chemin chemin : cheminsRestants) {
            if (!chemin.destinations.isEmpty()) {
                Position destination = chemin.destinations.get(0);
                boolean dejaPresente = destinations.stream().anyMatch(position ->
                        position.ligne == destination.ligne && position.colonne == destination.colonne);
                if (!dejaPresente) {
                    destinations.add(destination);
                }
            }
        }
        return destinations;
    }

    private static Piece[][] copierPlateau(Piece[][] original) {
        Piece[][] copie = new Piece[NOMBRE_CASES][NOMBRE_CASES];
        for (int ligne = 0; ligne < NOMBRE_CASES; ligne++) {
            for (int colonne = 0; colonne < NOMBRE_CASES; colonne++) {
                if (original[ligne][colonne] != null) {
                    copie[ligne][colonne] = original[ligne][colonne].copie(); // Copie indépendante pour éviter de modifier la partie réelle.
                }
            }
        }
        return copie;
    }

    private static boolean estLigneDePromotion(int ligne, int couleur) {
        return couleur == BLANC ? ligne == 0 : ligne == NOMBRE_CASES - 1;
    }

    private static boolean dansPlateau(int ligne, int colonne) {
        return ligne >= 0 && ligne < NOMBRE_CASES && colonne >= 0 && colonne < NOMBRE_CASES;
    }

    private int ligneAffichee(int ligne) {
        return couleurLocale == BLANC ? ligne : NOMBRE_CASES - 1 - ligne;
    }

    private int colonneAffichee(int colonne) {
        return couleurLocale == BLANC ? colonne : NOMBRE_CASES - 1 - colonne;
    }

    private final class PanneauPlateau extends JPanel {
        private PanneauPlateau() {
            setPreferredSize(new Dimension(620, 620));
            addMouseListener(new MouseAdapter() {
                @Override
                public void mousePressed(MouseEvent event) {
                    gererClic(event.getX(), event.getY());
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int taille = Math.min(getWidth(), getHeight());
            if (taille == 0) {
                g.dispose();
                return;
            }
            int tailleCase = taille / NOMBRE_CASES;
            int xDepart = (getWidth() - taille) / 2;
            int yDepart = (getHeight() - taille) / 2;

            for (int ligne = 0; ligne < NOMBRE_CASES; ligne++) {
                for (int colonne = 0; colonne < NOMBRE_CASES; colonne++) {
                    g.setColor((ligne + colonne) % 2 == 0
                            ? (modeNoel ? NEIGE : CASE_CLAIRE)
                            : (modeNoel ? BLEU_HIVER : CASE_FONCEE));
                    g.fillRect(xDepart + colonne * tailleCase, yDepart + ligne * tailleCase,
                            tailleCase, tailleCase);
                }
            }

            if (ligneSelectionnee >= 0) {
                g.setColor(new Color(255, 215, 0, 130));
                g.setStroke(new BasicStroke(4));
                g.drawRect(xDepart + colonneAffichee(colonneSelectionnee) * tailleCase + 2,
                        yDepart + ligneAffichee(ligneSelectionnee) * tailleCase + 2,
                        tailleCase - 4, tailleCase - 4);
            }
            g.setColor(DEPLACEMENT);
            for (Position destination : destinationsSelectionnees()) {
                int diametre = tailleCase / 3;
                g.fillOval(xDepart + colonneAffichee(destination.colonne) * tailleCase
                                + (tailleCase - diametre) / 2,
                        yDepart + ligneAffichee(destination.ligne) * tailleCase
                                + (tailleCase - diametre) / 2,
                        diametre, diametre);
            }

            for (int ligne = 0; ligne < NOMBRE_CASES; ligne++) {
                for (int colonne = 0; colonne < NOMBRE_CASES; colonne++) {
                    Piece piece = plateau[ligne][colonne];
                    if (piece == null) {
                        continue;
                    }
                    int marge = tailleCase / 6;
                    int x = xDepart + colonneAffichee(colonne) * tailleCase + marge;
                    int y = yDepart + ligneAffichee(ligne) * tailleCase + marge;
                    int diametre = tailleCase - 2 * marge;
                    g.setColor(piece.getCouleur() == BLANC
                            ? (modeNoel ? CREME : new Color(190, 45, 50))
                            : (modeNoel ? CANNEBERGE : new Color(40, 75, 150)));
                    g.fillOval(x, y, diametre, diametre);
                    g.setColor(modeNoel
                            ? (piece.getCouleur() == BLANC ? SAPIN : new Color(255, 247, 225))
                            : new Color(35, 35, 35, 150));
                    g.setStroke(new BasicStroke(modeNoel ? 3 : 2));
                    g.drawOval(x, y, diametre, diametre);
                    if (modeNoel) {
                        g.setColor(piece.getCouleur() == BLANC
                                ? new Color(203, 163, 76) : new Color(226, 173, 100));
                        g.setStroke(new BasicStroke(1.5f));
                        g.drawOval(x + diametre / 8, y + diametre / 8,
                                diametre * 3 / 4, diametre * 3 / 4);
                    }
                    if (piece.estDame()) {
                        g.setColor(modeNoel ? new Color(255, 220, 112) : Color.WHITE);
                        g.setFont(getFont().deriveFont(Font.BOLD, tailleCase * 0.55f));
                        String etoile = modeNoel ? "\u2605" : "*";
                        int largeur = g.getFontMetrics().stringWidth(etoile);
                        int hauteur = g.getFontMetrics().getAscent();
                        g.drawString(etoile, x + (diametre - largeur) / 2,
                                y + (diametre + hauteur) / 2 - tailleCase / 12);
                    } else if (modeNoel) {
                        dessinerFlocon(g, x + diametre / 2, y + diametre / 2, diametre * 0.28,
                                piece.getCouleur() == BLANC ? SAPIN : new Color(255, 247, 225));
                    }
                }
            }
            g.dispose();
        }
    }

    private static void dessinerFlocon(Graphics2D graphics, double centreX, double centreY,
                                       double rayon, Color couleur) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setColor(couleur);
        g.setStroke(new BasicStroke(1.8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int angle = 0; angle < 180; angle += 60) {
            double radians = Math.toRadians(angle);
            int dx = (int) Math.round(Math.cos(radians) * rayon);
            int dy = (int) Math.round(Math.sin(radians) * rayon);
            g.drawLine((int) centreX - dx, (int) centreY - dy,
                    (int) centreX + dx, (int) centreY + dy);
        }
        g.dispose();
    }

    private static final class Position {
        private final int ligne;
        private final int colonne;

        private Position(int ligne, int colonne) {
            this.ligne = ligne;
            this.colonne = colonne;
        }
    }

    private static final class Chemin {
        private final int ligneDepart;
        private final int colonneDepart;
        private final List<Position> destinations;
        private final boolean prise;

        private Chemin(int ligneDepart, int colonneDepart, List<Position> destinations, boolean prise) {
            this.ligneDepart = ligneDepart;
            this.colonneDepart = colonneDepart;
            this.destinations = destinations;
            this.prise = prise;
        }

        private Chemin suivant() {
            return new Chemin(ligneDepart, colonneDepart,
                    new ArrayList<>(destinations.subList(1, destinations.size())), prise);
        }
    }
}
