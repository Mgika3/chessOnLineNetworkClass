package jeuEchec;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Path2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.io.IOException;
import reseau.GameConnection;

/**
 * Plateau complet du jeu d'échecs et moteur de ses règles.
 *
 * Le tableau plateau est l'état source. Les coups sont représentés par de petits
 * objets Coup, puis recalculés légalement avant chaque sélection. Avant d'autoriser
 * un coup, une copie du plateau est créée et le mouvement y est appliqué afin de
 * vérifier que le roi du joueur n'y resterait pas en échec. Cette simulation évite
 * des cases particulières difficiles à traiter directement.
 */
public class JeuEchec extends JPanel {
    private static final int TAILLE = 8;
    private static final Color CASE_CLAIRE = new Color(238, 219, 180);
    private static final Color CASE_FONCEE = new Color(105, 65, 43);
    private static final Color NEIGE = new Color(237, 244, 249);
    private static final Color BLEU_HIVER = new Color(83, 108, 132);
    private static final Color MOUVEMENT = new Color(205, 205, 205, 185);

    private final boolean modeNoel;
    private final Piece[][] plateau = new Piece[TAILLE][TAILLE];
    private final JLabel statut = new JLabel("", SwingConstants.LEFT);
    private final JLabel horloge = new JLabel("Temps : 00:00:00", SwingConstants.RIGHT);
    private final PanneauPlateau panneauPlateau = new PanneauPlateau();
    private final long debutPartie = System.currentTimeMillis();
    private final Timer timer;
    private final Timer timerAnimation;
    private final List<DeplacementAnime> deplacementsAnimes = new ArrayList<>();
    private final Map<String, Integer> repetitions = new HashMap<>();
    private final GameConnection connexion;
    private final Piece.Couleur couleurLocale;

    private Piece.Couleur joueur = Piece.Couleur.BLANC;
    private int ligneSelectionnee = -1;
    private int colonneSelectionnee = -1;
    private int ligneEnPassant = -1;
    private int colonneEnPassant = -1;
    private int demiCoupsDepuisPriseOuPion;
    private List<Coup> coupsLegaux = Collections.emptyList();
    private boolean partieTerminee;

    public JeuEchec() {
        this(false);
    }

    public JeuEchec(boolean modeNoel) {
        this(modeNoel, null, true);
    }

    public JeuEchec(boolean modeNoel, GameConnection connexion, boolean joueurBlanc) {
        this.modeNoel = modeNoel;
        this.connexion = connexion;
        couleurLocale = joueurBlanc ? Piece.Couleur.BLANC : Piece.Couleur.NOIR;
        setLayout(new BorderLayout(8, 8));
        setBorder(BorderFactory.createEmptyBorder(10, 12, 12, 12));
        JPanel barreEtat = new JPanel(new BorderLayout());
        barreEtat.add(statut, BorderLayout.CENTER);
        barreEtat.add(horloge, BorderLayout.EAST);
        add(barreEtat, BorderLayout.NORTH);
        add(panneauPlateau, BorderLayout.CENTER);

        initialiserPlateau();
        coupsLegaux = calculerCoupsLegaux(joueur, plateau, ligneEnPassant, colonneEnPassant);
        repetitions.put(clePosition(), 1);
        actualiserStatut();
        timer = new Timer(1000, event -> actualiserHorloge());
        timer.start();
        timerAnimation = new Timer(16, event -> actualiserAnimations());
        timerAnimation.setCoalesce(true);
        if (connexion != null) {
            // Le thread reseau ne modifie jamais Swing directement : invokeLater remet
            // l'application du mouvement dans la file de l'Event Dispatch Thread.
            connexion.definirEcouteurCoups(coup -> SwingUtilities.invokeLater(
                    () -> appliquerCoupDistant(coup)));
        }
    }

    public void arreterHorloge() {
        timer.stop();
        timerAnimation.stop();
    }

    private void initialiserPlateau() {
        Piece.Type[] rangee = {
                Piece.Type.TOUR, Piece.Type.CAVALIER, Piece.Type.FOU, Piece.Type.DAME,
                Piece.Type.ROI, Piece.Type.FOU, Piece.Type.CAVALIER, Piece.Type.TOUR
        };
        for (int colonne = 0; colonne < TAILLE; colonne++) {
            plateau[0][colonne] = new Piece(Piece.Couleur.NOIR, rangee[colonne]);
            plateau[1][colonne] = new Piece(Piece.Couleur.NOIR, Piece.Type.PION);
            plateau[6][colonne] = new Piece(Piece.Couleur.BLANC, Piece.Type.PION);
            plateau[7][colonne] = new Piece(Piece.Couleur.BLANC, rangee[colonne]);
        }
    }

    private void actualiserHorloge() {
        long secondes = (System.currentTimeMillis() - debutPartie) / 1000;
        horloge.setText(String.format("Temps : %02d:%02d:%02d", secondes / 3600,
                (secondes / 60) % 60, secondes % 60));
    }

    private void actualiserAnimations() {
        long maintenant = System.nanoTime();
        deplacementsAnimes.removeIf(deplacement ->
                maintenant - deplacement.debut >= deplacement.duree);
        panneauPlateau.repaint();
        if (deplacementsAnimes.isEmpty()) {
            timerAnimation.stop();
        }
    }

    private void actualiserStatut() {
        if (partieTerminee) {
            return;
        }
        String nomJoueur = joueur == Piece.Couleur.BLANC ? "Blancs" : "Noirs";
        String instruction = ligneSelectionnee < 0
                ? "sélectionnez une pièce" : "choisissez une case grisée";
        String message = "Au tour des " + nomJoueur + " — " + instruction;
        if (estEnEchec(plateau, joueur)) {
            message = "Échec ! " + message;
        }
        if (connexion != null) {
            message += joueur == couleurLocale ? " — à vous" : " — tour adverse";
        }
        statut.setText(message);
    }

    private List<Coup> calculerCoupsLegaux(Piece.Couleur couleur, Piece[][] etat,
                                           int epLigne, int epColonne) {
        /*
         * C'est le filtre principal de sécurité des échecs : chaque mouvement brut est
         * simulé sur un plateau copié. On conserve seulement les mouvements dont la couleur
         * jouante ne laisse pas son propre roi en échec.
         */
        List<Coup> coups = new ArrayList<>();
        for (int ligne = 0; ligne < TAILLE; ligne++) {
            for (int colonne = 0; colonne < TAILLE; colonne++) {
                Piece piece = etat[ligne][colonne];
                if (piece == null || piece.getCouleur() != couleur) {
                    continue;
                }
                for (Coup coup : calculerCoupsPossibles(ligne, colonne, etat, epLigne, epColonne)) {
                    Piece[][] simulation = copierPlateau(etat);
                    appliquerCoup(simulation, coup);
                    if (!estEnEchec(simulation, couleur)) {
                        coups.add(coup);
                    }
                }
            }
        }
        return coups;
    }

    private List<Coup> calculerCoupsPossibles(int ligne, int colonne, Piece[][] etat,
                                               int epLigne, int epColonne) {
        Piece piece = etat[ligne][colonne];
        List<Coup> coups = new ArrayList<>();
        switch (piece.getType()) {
            case PION:
                ajouterCoupsPion(coups, ligne, colonne, etat, epLigne, epColonne, piece);
                break;
            case CAVALIER:
                int[][] sauts = {{-2, -1}, {-2, 1}, {-1, -2}, {-1, 2},
                        {1, -2}, {1, 2}, {2, -1}, {2, 1}};
                for (int[] saut : sauts) {
                    ajouterCoupSiLibreOuAdverse(coups, ligne, colonne,
                            ligne + saut[0], colonne + saut[1], etat, piece);
                }
                break;
            case ROI:
                for (int dl = -1; dl <= 1; dl++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        if (dl != 0 || dc != 0) {
                            ajouterCoupSiLibreOuAdverse(coups, ligne, colonne,
                                    ligne + dl, colonne + dc, etat, piece);
                        }
                    }
                }
                ajouterCoupsRoque(coups, ligne, colonne, etat, piece);
                break;
            case TOUR:
                ajouterCoupsGlisses(coups, ligne, colonne, etat, piece,
                        new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1}});
                break;
            case FOU:
                ajouterCoupsGlisses(coups, ligne, colonne, etat, piece,
                        new int[][]{{-1, -1}, {-1, 1}, {1, -1}, {1, 1}});
                break;
            case DAME:
                ajouterCoupsGlisses(coups, ligne, colonne, etat, piece,
                        new int[][]{{-1, 0}, {1, 0}, {0, -1}, {0, 1},
                                {-1, -1}, {-1, 1}, {1, -1}, {1, 1}});
                break;
            default:
                throw new IllegalStateException("Type de pièce inconnu.");
        }
        return coups;
    }

    private void ajouterCoupsPion(List<Coup> coups, int ligne, int colonne, Piece[][] etat,
                                  int epLigne, int epColonne, Piece pion) {
        int direction = pion.getCouleur() == Piece.Couleur.BLANC ? -1 : 1; // Les Blancs avancent vers les lignes 0, les Noirs vers les lignes 7.
        int ligneAvancee = ligne + direction;
        if (dansPlateau(ligneAvancee, colonne) && etat[ligneAvancee][colonne] == null) {
            ajouterCoupPionAvecPromotion(coups, ligne, colonne, ligneAvancee, colonne);
            int ligneDeuxPas = ligne + direction * 2; // Le double pas n'est envisageable que depuis la rangée de départ.
            int ligneDepart = pion.getCouleur() == Piece.Couleur.BLANC ? 6 : 1; // Rangée initiale des pions blancs ou noirs.
            if (ligne == ligneDepart && etat[ligneDeuxPas][colonne] == null) {
                coups.add(new Coup(ligne, colonne, ligneDeuxPas, colonne));
            }
        }
        for (int dc : new int[]{-1, 1}) {
            int cibleColonne = colonne + dc;
            if (!dansPlateau(ligneAvancee, cibleColonne)) {
                continue;
            }
            Piece cible = etat[ligneAvancee][cibleColonne];
            if (cible != null && cible.getCouleur() != pion.getCouleur()
                    && cible.getType() != Piece.Type.ROI) {
                ajouterCoupPionAvecPromotion(coups, ligne, colonne, ligneAvancee, cibleColonne);
            } else if (ligneAvancee == epLigne && cibleColonne == epColonne
                    && etat[ligne][cibleColonne] != null
                    && etat[ligne][cibleColonne].getType() == Piece.Type.PION
                    && etat[ligne][cibleColonne].getCouleur() != pion.getCouleur()) {
                // En passant : le pion adverse a avance de deux cases au tour precedent.
                // epLigne/epColonne indiquent la case d'arrivee du pion qui capture.
                coups.add(new Coup(ligne, colonne, ligneAvancee, cibleColonne, null, true, false));
            }
        }
    }

    private void ajouterCoupPionAvecPromotion(List<Coup> coups, int ligneDepart, int colonneDepart,
                                               int ligneArrivee, int colonneArrivee) {
        int derniereRangee = ligneArrivee == 0 ? 0 : TAILLE - 1;
        if (ligneArrivee != derniereRangee) {
            coups.add(new Coup(ligneDepart, colonneDepart, ligneArrivee, colonneArrivee));
            return;
        }
        for (Piece.Type type : new Piece.Type[]{Piece.Type.DAME, Piece.Type.TOUR,
                Piece.Type.FOU, Piece.Type.CAVALIER}) {
            coups.add(new Coup(ligneDepart, colonneDepart, ligneArrivee, colonneArrivee, type, false, false));
        }
    }

    private void ajouterCoupsRoque(List<Coup> coups, int ligne, int colonne,
                                   Piece[][] etat, Piece roi) {
        // Le roque est refuse si le roi a bouge, s'il est en echec, ou si une case
        // qu'il traverse est attaquee : ces conditions sont distinctes du chemin libre.
        int ligneDepart = roi.getCouleur() == Piece.Couleur.BLANC ? 7 : 0;
        if (ligne != ligneDepart || colonne != 4 || roi.aBouge()
                || caseAttaquee(etat, ligne, colonne, roi.getCouleur().adverse())) {
            return;
        }
        Piece tourRoi = etat[ligneDepart][7];
        if (tourRoi != null && tourRoi.getType() == Piece.Type.TOUR
                && tourRoi.getCouleur() == roi.getCouleur() && !tourRoi.aBouge()
                && etat[ligneDepart][5] == null && etat[ligneDepart][6] == null
                && !caseAttaqueeAvecRoiEn(etat, ligneDepart, colonne, ligneDepart, 5,
                roi.getCouleur().adverse())
                && !caseAttaqueeAvecRoiEn(etat, ligneDepart, colonne, ligneDepart, 6,
                roi.getCouleur().adverse())) {
            coups.add(new Coup(ligne, colonne, ligneDepart, 6, null, false, true));
        }
        Piece tourDame = etat[ligneDepart][0];
        if (tourDame != null && tourDame.getType() == Piece.Type.TOUR
                && tourDame.getCouleur() == roi.getCouleur() && !tourDame.aBouge()
                && etat[ligneDepart][1] == null && etat[ligneDepart][2] == null
                && etat[ligneDepart][3] == null
                && !caseAttaqueeAvecRoiEn(etat, ligneDepart, colonne, ligneDepart, 3,
                roi.getCouleur().adverse())
                && !caseAttaqueeAvecRoiEn(etat, ligneDepart, colonne, ligneDepart, 2,
                roi.getCouleur().adverse())) {
            coups.add(new Coup(ligne, colonne, ligneDepart, 2, null, false, true));
        }
    }

    private boolean caseAttaqueeAvecRoiEn(Piece[][] etat, int departLigne, int departColonne,
                                          int arriveeLigne, int arriveeColonne,
                                          Piece.Couleur attaquant) {
        // Simule le roi sur sa case de passage pour détecter les attaques révélées par son déplacement.
        Piece[][] simulation = copierPlateau(etat);
        simulation[arriveeLigne][arriveeColonne] = simulation[departLigne][departColonne];
        simulation[departLigne][departColonne] = null;
        return caseAttaquee(simulation, arriveeLigne, arriveeColonne, attaquant);
    }

    private void ajouterCoupsGlisses(List<Coup> coups, int ligne, int colonne, Piece[][] etat,
                                     Piece piece, int[][] directions) {
        for (int[] direction : directions) {
            int l = ligne + direction[0];
            int c = colonne + direction[1];
            while (dansPlateau(l, c)) {
                Piece cible = etat[l][c];
                if (cible == null) {
                    coups.add(new Coup(ligne, colonne, l, c));
                } else {
                    if (cible.getCouleur() != piece.getCouleur() && cible.getType() != Piece.Type.ROI) {
                        coups.add(new Coup(ligne, colonne, l, c));
                    }
                    break;
                }
                l += direction[0];
                c += direction[1];
            }
        }
    }

    private void ajouterCoupSiLibreOuAdverse(List<Coup> coups, int ligne, int colonne,
                                              int ligneArrivee, int colonneArrivee,
                                              Piece[][] etat, Piece piece) {
        if (!dansPlateau(ligneArrivee, colonneArrivee)) {
            return;
        }
        Piece cible = etat[ligneArrivee][colonneArrivee];
        if (cible == null || (cible.getCouleur() != piece.getCouleur()
                && cible.getType() != Piece.Type.ROI)) {
            coups.add(new Coup(ligne, colonne, ligneArrivee, colonneArrivee));
        }
    }

    private boolean estEnEchec(Piece[][] etat, Piece.Couleur couleur) {
        int[] roi = trouverRoi(etat, couleur);
        return roi != null && caseAttaquee(etat, roi[0], roi[1], couleur.adverse());
    }

    private int[] trouverRoi(Piece[][] etat, Piece.Couleur couleur) {
        for (int ligne = 0; ligne < TAILLE; ligne++) {
            for (int colonne = 0; colonne < TAILLE; colonne++) {
                Piece piece = etat[ligne][colonne];
                if (piece != null && piece.getCouleur() == couleur
                        && piece.getType() == Piece.Type.ROI) {
                    return new int[]{ligne, colonne};
                }
            }
        }
        return null;
    }

    private boolean caseAttaquee(Piece[][] etat, int ligneCible, int colonneCible,
                                 Piece.Couleur attaquant) {
        // Cette fonction calcule les cases controlees, pas les coups legaux.
        // C'est important pour les pions et pour les cases traversees par le roi au roque.
        for (int ligne = 0; ligne < TAILLE; ligne++) {
            for (int colonne = 0; colonne < TAILLE; colonne++) {
                Piece piece = etat[ligne][colonne];
                if (piece == null || piece.getCouleur() != attaquant) {
                    continue;
                }
                int dl = ligneCible - ligne;
                int dc = colonneCible - colonne;
                switch (piece.getType()) {
                    case PION:
                        int direction = attaquant == Piece.Couleur.BLANC ? -1 : 1;
                        if (dl == direction && Math.abs(dc) == 1) {
                            return true;
                        }
                        break;
                    case CAVALIER:
                        if (Math.abs(dl) * Math.abs(dc) == 2) {
                            return true;
                        }
                        break;
                    case ROI:
                        if (Math.max(Math.abs(dl), Math.abs(dc)) == 1) {
                            return true;
                        }
                        break;
                    case TOUR:
                        if ((dl == 0 || dc == 0) && cheminLibre(etat, ligne, colonne, ligneCible, colonneCible)) {
                            return true;
                        }
                        break;
                    case FOU:
                        if (Math.abs(dl) == Math.abs(dc)
                                && cheminLibre(etat, ligne, colonne, ligneCible, colonneCible)) {
                            return true;
                        }
                        break;
                    case DAME:
                        if ((dl == 0 || dc == 0 || Math.abs(dl) == Math.abs(dc))
                                && cheminLibre(etat, ligne, colonne, ligneCible, colonneCible)) {
                            return true;
                        }
                        break;
                    default:
                        throw new IllegalStateException("Type de pièce inconnu.");
                }
            }
        }
        return false;
    }

    private boolean cheminLibre(Piece[][] etat, int ligneDepart, int colonneDepart,
                                int ligneArrivee, int colonneArrivee) {
        // Les tours, fous et dames peuvent glisser seulement si toutes les cases
        // intermediaires sont vides ; la case d'arrivee est testee par l'appelant.
        int pasLigne = Integer.compare(ligneArrivee, ligneDepart);
        int pasColonne = Integer.compare(colonneArrivee, colonneDepart);
        int ligne = ligneDepart + pasLigne;
        int colonne = colonneDepart + pasColonne;
        while (ligne != ligneArrivee || colonne != colonneArrivee) {
            if (etat[ligne][colonne] != null) {
                return false;
            }
            ligne += pasLigne;
            colonne += pasColonne;
        }
        return true;
    }

    private void appliquerCoup(Piece[][] etat, Coup coup) {
        /*
         * Cette méthode centralise toutes les transformations d'un mouvement :
         * prise, prise en passant, promotion, roque et mise à jour de l'état enPassant.
         * Les simulations l'utilisent aussi, ce qui garantit qu'elles suivent exactement
         * les mêmes règles que le vrai mouvement.
         */
        Piece piece = etat[coup.ligneDepart][coup.colonneDepart];
        etat[coup.ligneDepart][coup.colonneDepart] = null;
        if (coup.enPassant) {
            etat[coup.ligneDepart][coup.colonneArrivee] = null;
        }
        piece.marquerCommeDeplacee();
        if (coup.promotion != null) {
            piece.promouvoir(coup.promotion);
        }
        etat[coup.ligneArrivee][coup.colonneArrivee] = piece;
        if (coup.roque) {
            int colonneTourDepart = coup.colonneArrivee == 6 ? 7 : 0;
            int colonneTourArrivee = coup.colonneArrivee == 6 ? 5 : 3;
            Piece tour = etat[coup.ligneDepart][colonneTourDepart];
            etat[coup.ligneDepart][colonneTourDepart] = null;
            tour.marquerCommeDeplacee();
            etat[coup.ligneDepart][colonneTourArrivee] = tour;
        }
    }

    private void jouerCoup(Coup coup, boolean transmettre) {
        Piece piece = plateau[coup.ligneDepart][coup.colonneDepart]; // Pièce réellement déplacée.
        boolean deplacementPion = piece.getType() == Piece.Type.PION; // Sert à remettre à zéro la règle des 50 coups.
        boolean prise = plateau[coup.ligneArrivee][coup.colonneArrivee] != null || coup.enPassant; // Détecte une capture normale ou en passant.
        if (coup.promotion != null && transmettre) {
            Piece.Type promotion = choisirPromotion();
            coup = new Coup(coup.ligneDepart, coup.colonneDepart, coup.ligneArrivee,
                    coup.colonneArrivee, promotion, coup.enPassant, coup.roque);
        }
        if (transmettre && connexion != null) {
            // En mode reseau, l'emetteur envoie le coup avant de modifier son plateau.
            // Si l'ecriture echoue, on abandonne localement pour eviter une desynchronisation.
            String message = coup.ligneDepart + "|" + coup.colonneDepart + "|"
                    + coup.ligneArrivee + "|" + coup.colonneArrivee + "|"
                    + (coup.promotion == null ? "-" : coup.promotion.name()); // Encodage compact du mouvement.
            try {
                connexion.envoyerCoup(message);
            } catch (IOException exception) {
                statut.setText("Coup non envoyé : " + exception.getMessage());
                return;
            }
        }
        demarrerAnimation(coup);
        appliquerCoup(plateau, coup);
        ligneEnPassant = -1;
        colonneEnPassant = -1;
        if (piece.getType() == Piece.Type.PION && Math.abs(coup.ligneArrivee - coup.ligneDepart) == 2) {
            ligneEnPassant = (coup.ligneDepart + coup.ligneArrivee) / 2;
            colonneEnPassant = coup.colonneDepart;
        }

        demiCoupsDepuisPriseOuPion = prise || deplacementPion
                ? 0 : demiCoupsDepuisPriseOuPion + 1;
        joueur = joueur.adverse();
        ligneSelectionnee = -1;
        colonneSelectionnee = -1;
        coupsLegaux = calculerCoupsLegaux(joueur, plateau, ligneEnPassant, colonneEnPassant);
        String cle = clePosition();
        repetitions.put(cle, repetitions.getOrDefault(cle, 0) + 1);
        verifierFinPartie();
        actualiserStatut();
        panneauPlateau.repaint();
    }

    private void demarrerAnimation(Coup coup) {
        Piece piece = plateau[coup.ligneDepart][coup.colonneDepart];
        long debut = System.nanoTime();
        int distance = Math.max(Math.abs(coup.ligneArrivee - coup.ligneDepart),
                Math.abs(coup.colonneArrivee - coup.colonneDepart));
        long duree = (220L + Math.max(0, distance - 1) * 35L) * 1_000_000L;
        deplacementsAnimes.add(new DeplacementAnime(piece, coup.ligneDepart, coup.colonneDepart,
                coup.ligneArrivee, coup.colonneArrivee, debut, duree));
        if (coup.roque) {
            int colonneTourDepart = coup.colonneArrivee == 6 ? 7 : 0;
            int colonneTourArrivee = coup.colonneArrivee == 6 ? 5 : 3;
            deplacementsAnimes.add(new DeplacementAnime(
                    plateau[coup.ligneDepart][colonneTourDepart], coup.ligneDepart,
                    colonneTourDepart, coup.ligneDepart, colonneTourArrivee, debut, duree));
        }
        timerAnimation.start();
    }

    private void appliquerCoupDistant(String message) {
        // Les coups distants passent par SwingUtilities.invokeLater pour être appliqués
        // sur l'unique thread Swing. Le garde "joueur == couleurLocale" évite un echo local.
        if (partieTerminee || connexion == null || joueur == couleurLocale) {
            return;
        }
        String[] champs = message.split("\\|", -1); // Six paramètres sont attendus pour reproduire exactement le coup.
        if (champs.length != 5) {
            statut.setText("Coup reçu invalide.");
            return;
        }
        try {
            int ligneDepart = Integer.parseInt(champs[0]);
            int colonneDepart = Integer.parseInt(champs[1]);
            int ligneArrivee = Integer.parseInt(champs[2]);
            int colonneArrivee = Integer.parseInt(champs[3]);
            Piece.Type promotion = "-".equals(champs[4])
                    ? null : Piece.Type.valueOf(champs[4]);
            if (!dansPlateau(ligneDepart, colonneDepart)
                    || !dansPlateau(ligneArrivee, colonneArrivee)) {
                throw new IllegalArgumentException();
            }
            // Le client ne fait pas confiance au texte reseau : il recherche ce mouvement
            // dans ses propres coups legaux avant de modifier son etat local.
            for (Coup coup : coupsPour(ligneDepart, colonneDepart, ligneArrivee, colonneArrivee)) {
                if (coup.promotion == promotion) {
                    jouerCoup(coup, false);
                    return;
                }
            }
        } catch (IllegalArgumentException exception) {
            statut.setText("Coup reçu invalide.");
            return;
        }
        statut.setText("Coup reçu illégal, partie synchronisée interrompue.");
    }

    private Piece.Type choisirPromotion() {
        Piece.Type[] types = {Piece.Type.DAME, Piece.Type.TOUR, Piece.Type.FOU, Piece.Type.CAVALIER};
        String[] choix = {"Dame", "Tour", "Fou", "Cavalier"};
        int selection = JOptionPane.showOptionDialog(this, "Choisissez la pièce de promotion :",
                "Promotion du pion", JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE,
                null, choix, choix[0]);
        return selection >= 0 ? types[selection] : Piece.Type.DAME;
    }

    private void verifierFinPartie() {
        // Cette verification est faite apres chaque coup, une fois que le joueur actif
        // et sa liste de coups legaux ont ete recalcules.
        if (coupsLegaux.isEmpty()) {
            partieTerminee = true;
            if (estEnEchec(plateau, joueur)) {
                statut.setText("Échec et mat — victoire des "
                        + (joueur.adverse() == Piece.Couleur.BLANC ? "Blancs" : "Noirs") + " !");
            } else {
                statut.setText("Partie nulle par pat.");
            }
        } else if (demiCoupsDepuisPriseOuPion >= 100) {
            terminerNulle("règle des 50 coups");
        } else if (repetitions.getOrDefault(clePosition(), 0) >= 3) {
            terminerNulle("répétition triple");
        } else if (materielInsuffisant()) {
            terminerNulle("matériel insuffisant");
        }
        if (partieTerminee) {
            timer.stop();
        }
    }

    private void terminerNulle(String raison) {
        partieTerminee = true;
        statut.setText("Partie nulle (" + raison + ").");
    }

    private boolean materielInsuffisant() {
        List<int[]> mineurs = new ArrayList<>();
        for (int ligne = 0; ligne < TAILLE; ligne++) {
            for (int colonne = 0; colonne < TAILLE; colonne++) {
                Piece piece = plateau[ligne][colonne];
                if (piece != null && piece.getType() != Piece.Type.ROI) {
                    if (piece.getType() == Piece.Type.PION || piece.getType() == Piece.Type.TOUR
                            || piece.getType() == Piece.Type.DAME) {
                        return false;
                    }
                    mineurs.add(new int[]{ligne, colonne, piece.getType() == Piece.Type.FOU ? 1 : 0});
                }
            }
        }
        if (mineurs.size() <= 1) {
            return true;
        }
        if (mineurs.size() == 2 && mineurs.get(0)[2] == 1 && mineurs.get(1)[2] == 1) {
            return ((mineurs.get(0)[0] + mineurs.get(0)[1]) % 2)
                    == ((mineurs.get(1)[0] + mineurs.get(1)[1]) % 2);
        }
        return false;
    }

    private String clePosition() {
        // La clé est sans horloge ni nombre de coups : elle décrit uniquement la position,
        // le joueur actif, le droit de roque et la prise en passant. Cela permet de détecter
        // correctement les répétitions au lieu de compter des coups différents artificiellement.
        StringBuilder cle = new StringBuilder(); // Encodage compact de chaque case du plateau.
        for (int ligne = 0; ligne < TAILLE; ligne++) {
            for (int colonne = 0; colonne < TAILLE; colonne++) {
                Piece piece = plateau[ligne][colonne];
                cle.append(piece == null ? '-' : piece.getCouleur().name().charAt(0)
                        + piece.getType().name().substring(0, 1));
            }
        }
        cle.append(joueur == Piece.Couleur.BLANC ? 'b' : 'n'); // Deux plateaux identiques avec un joueur different ne sont pas la meme position.
        for (Piece.Couleur couleur : Piece.Couleur.values()) {
            int ligne = couleur == Piece.Couleur.BLANC ? 7 : 0;
            Piece roi = plateau[ligne][4];
            Piece tourRoi = plateau[ligne][7];
            Piece tourDame = plateau[ligne][0];
            cle.append(roi != null && !roi.aBouge()
                    && tourRoi != null && !tourRoi.aBouge() ? 'R' : '-');
            cle.append(roi != null && !roi.aBouge()
                    && tourDame != null && !tourDame.aBouge() ? 'Q' : '-');
        }
        cle.append(ligneEnPassant).append(':').append(colonneEnPassant);
        return cle.toString();
    }

    private void gererClic(int x, int y) {
        if (partieTerminee || !deplacementsAnimes.isEmpty()) {
            return;
        }
        // En partie en ligne, une entree utilisateur n'est acceptee que pendant son propre tour.
        if (connexion != null && joueur != couleurLocale) {
            return;
        }
        int taille = Math.min(panneauPlateau.getWidth(), panneauPlateau.getHeight());
        int tailleCase = taille / TAILLE;
        if (tailleCase == 0) {
            return;
        }
        int xDepart = (panneauPlateau.getWidth() - taille) / 2;
        int yDepart = (panneauPlateau.getHeight() - taille) / 2;
        if (x < xDepart || y < yDepart) {
            return;
        }
        int colonne = (x - xDepart) / tailleCase;
        int ligne = (y - yDepart) / tailleCase;
        if (!dansPlateau(ligne, colonne)) {
            return;
        }
        // Le plateau noir est dessine tourne ; les coordonnees de souris sont inversees
        // pour continuer a utiliser le meme tableau de jeu pour les deux joueurs.
        if (couleurLocale == Piece.Couleur.NOIR) {
            ligne = TAILLE - 1 - ligne;
            colonne = TAILLE - 1 - colonne;
        }
        if (ligneSelectionnee >= 0) {
            List<Coup> destination = coupsPour(ligneSelectionnee, colonneSelectionnee, ligne, colonne);
            if (!destination.isEmpty()) {
                jouerCoup(destination.get(0), true);
                return;
            }
        }
        Piece piece = plateau[ligne][colonne];
        if (piece != null && piece.getCouleur() == joueur && possedeMouvementLegal(ligne, colonne)) {
            ligneSelectionnee = ligne;
            colonneSelectionnee = colonne;
        } else {
            ligneSelectionnee = -1;
            colonneSelectionnee = -1;
        }
        actualiserStatut();
        panneauPlateau.repaint();
    }

    private boolean possedeMouvementLegal(int ligne, int colonne) {
        for (Coup coup : coupsLegaux) {
            if (coup.ligneDepart == ligne && coup.colonneDepart == colonne) {
                return true;
            }
        }
        return false;
    }

    private List<Coup> coupsPour(int ligneDepart, int colonneDepart,
                                  int ligneArrivee, int colonneArrivee) {
        List<Coup> resultats = new ArrayList<>();
        for (Coup coup : coupsLegaux) {
            if (coup.ligneDepart == ligneDepart && coup.colonneDepart == colonneDepart
                    && coup.ligneArrivee == ligneArrivee && coup.colonneArrivee == colonneArrivee) {
                resultats.add(coup);
            }
        }
        return resultats;
    }

    private List<int[]> destinationsSelectionnees() {
        List<int[]> destinations = new ArrayList<>();
        for (Coup coup : coupsLegaux) {
            if (coup.ligneDepart == ligneSelectionnee && coup.colonneDepart == colonneSelectionnee) {
                boolean dejaPresente = destinations.stream().anyMatch(position ->
                        position[0] == coup.ligneArrivee && position[1] == coup.colonneArrivee);
                if (!dejaPresente) {
                    destinations.add(new int[]{coup.ligneArrivee, coup.colonneArrivee});
                }
            }
        }
        return destinations;
    }

    private static Piece[][] copierPlateau(Piece[][] original) {
        Piece[][] copie = new Piece[TAILLE][TAILLE];
        for (int ligne = 0; ligne < TAILLE; ligne++) {
            for (int colonne = 0; colonne < TAILLE; colonne++) {
                if (original[ligne][colonne] != null) {
                    copie[ligne][colonne] = original[ligne][colonne].copie();
                }
            }
        }
        return copie;
    }

    private static boolean dansPlateau(int ligne, int colonne) {
        return ligne >= 0 && ligne < TAILLE && colonne >= 0 && colonne < TAILLE;
    }

    private int ligneAffichee(int ligne) {
        return couleurLocale == Piece.Couleur.BLANC ? ligne : TAILLE - 1 - ligne;
    }

    private int colonneAffichee(int colonne) {
        return couleurLocale == Piece.Couleur.BLANC ? colonne : TAILLE - 1 - colonne;
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
            int tailleCase = taille / TAILLE;
            int xDepart = (getWidth() - taille) / 2;
            int yDepart = (getHeight() - taille) / 2;
            for (int ligne = 0; ligne < TAILLE; ligne++) {
                for (int colonne = 0; colonne < TAILLE; colonne++) {
                    g.setColor((ligne + colonne) % 2 == 0
                            ? (modeNoel ? NEIGE : CASE_CLAIRE)
                            : (modeNoel ? BLEU_HIVER : CASE_FONCEE));
                    g.fillRect(xDepart + colonne * tailleCase, yDepart + ligne * tailleCase,
                            tailleCase, tailleCase);
                }
            }
            if (ligneSelectionnee >= 0) {
                g.setColor(new Color(255, 215, 0, 135));
                g.setStroke(new BasicStroke(4));
                g.drawRect(xDepart + colonneAffichee(colonneSelectionnee) * tailleCase + 2,
                        yDepart + ligneAffichee(ligneSelectionnee) * tailleCase + 2,
                        tailleCase - 4, tailleCase - 4);
                g.setColor(MOUVEMENT);
                for (int[] destination : destinationsSelectionnees()) {
                    int diametre = tailleCase / 3;
                    g.fillOval(xDepart + colonneAffichee(destination[1]) * tailleCase
                                    + (tailleCase - diametre) / 2,
                            yDepart + ligneAffichee(destination[0]) * tailleCase
                                    + (tailleCase - diametre) / 2,
                            diametre, diametre);
                }
            }
            for (int ligne = 0; ligne < TAILLE; ligne++) {
                for (int colonne = 0; colonne < TAILLE; colonne++) {
                    Piece piece = plateau[ligne][colonne];
                    if (piece == null || estAnimee(piece)) {
                        continue;
                    }
                    int xCase = xDepart + colonneAffichee(colonne) * tailleCase;
                    int yCase = yDepart + ligneAffichee(ligne) * tailleCase;
                    dessinerPiece(g, piece, xCase, yCase, tailleCase);
                }
            }
            long maintenant = System.nanoTime();
            for (DeplacementAnime deplacement : deplacementsAnimes) {
                double progression = deplacement.progression(maintenant);
                double adoucie = progression * progression * (3 - 2 * progression);
                double departX = xDepart + colonneAffichee(deplacement.colonneDepart) * tailleCase;
                double departY = yDepart + ligneAffichee(deplacement.ligneDepart) * tailleCase;
                double arriveeX = xDepart + colonneAffichee(deplacement.colonneArrivee) * tailleCase;
                double arriveeY = yDepart + ligneAffichee(deplacement.ligneArrivee) * tailleCase;
                dessinerPiece(g, deplacement.piece,
                        (int) Math.round(departX + (arriveeX - departX) * adoucie),
                        (int) Math.round(departY + (arriveeY - departY) * adoucie), tailleCase);
            }
            g.dispose();
        }
    }

    private boolean estAnimee(Piece piece) {
        return deplacementsAnimes.stream().anyMatch(deplacement -> deplacement.piece == piece);
    }

    private void dessinerPiece(Graphics2D graphics, Piece piece, int xCase, int yCase, int tailleCase) {
        graphics.setFont(new Font(Font.SERIF, Font.PLAIN, (int) (tailleCase * 0.82)));
        graphics.setColor(piece.getCouleur() == Piece.Couleur.BLANC
                ? (modeNoel ? new Color(174, 42, 55) : new Color(250, 250, 245))
                : (modeNoel ? new Color(0, 163, 108) : new Color(28, 30, 35)));
        String symbole = piece.symbole();
        int largeur = graphics.getFontMetrics().stringWidth(symbole);
        int hauteur = graphics.getFontMetrics().getAscent();
        int x = xCase + (tailleCase - largeur) / 2;
        int y = yCase + hauteur - tailleCase / 10;
        graphics.drawString(symbole, x, y);
        if (modeNoel) {
            switch (piece.getType()) {
                case ROI:
                    dessinerBonnet(graphics, xCase + tailleCase * 0.29,
                            yCase + tailleCase * 0.08, tailleCase * 0.42, tailleCase * 0.27);
                    break;
                case DAME:
                    dessinerHoux(graphics, xCase + tailleCase * 0.66,
                            yCase + tailleCase * 0.08, tailleCase * 0.22);
                    break;
                case TOUR:
                    dessinerGuirlande(graphics, xCase, yCase, tailleCase);
                    break;
                case FOU:
                    dessinerMitra(graphics, xCase, yCase, tailleCase);
                    break;
                case CAVALIER:
                    dessinerEcharpe(graphics, xCase, yCase, tailleCase);
                    break;
                case PION:
                    dessinerFlocon(graphics, xCase + tailleCase * 0.67,
                            yCase + tailleCase * 0.63, tailleCase * 0.085);
                    break;
                default:
                    throw new IllegalStateException("Type de pièce inconnu.");
            }
        }
    }

    private static void dessinerBonnet(Graphics2D graphics, double x, double y, double largeur, double hauteur) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setColor(new Color(125, 29, 38));
        g.fillRoundRect((int) x, (int) (y + hauteur * 0.68), (int) largeur, (int) (hauteur * 0.32),
                Math.max(2, (int) (hauteur * 0.25)), Math.max(2, (int) (hauteur * 0.25)));
        Path2D.Double bonnet = new Path2D.Double();
        bonnet.moveTo(x + largeur * 0.08, y + hauteur * 0.76);
        bonnet.lineTo(x + largeur * 0.78, y + hauteur * 0.76);
        bonnet.lineTo(x + largeur * 0.66, y + hauteur * 0.36);
        bonnet.lineTo(x + largeur * 0.48, y + hauteur * 0.02);
        bonnet.closePath();
        g.setColor(new Color(205, 43, 52));
        g.fill(bonnet);
        g.setColor(new Color(248, 248, 239));
        g.fillRoundRect((int) x, (int) (y + hauteur * 0.68), (int) largeur, (int) (hauteur * 0.3),
                Math.max(2, (int) (hauteur * 0.25)), Math.max(2, (int) (hauteur * 0.25)));
        double pompon = hauteur * 0.25;
        g.fillOval((int) (x + largeur * 0.38), (int) y, (int) pompon, (int) pompon);
        g.dispose();
    }

    private static void dessinerGuirlande(Graphics2D graphics, int x, int y, int taille) {
        Graphics2D g = (Graphics2D) graphics.create();
        double s = taille;
        Path2D.Double guirlande = new Path2D.Double();
        guirlande.moveTo(x + s * 0.23, y + s * 0.48);
        guirlande.curveTo(x + s * 0.38, y + s * 0.56, x + s * 0.62, y + s * 0.56,
                x + s * 0.77, y + s * 0.48);
        g.setStroke(new BasicStroke((float) (s * 0.045), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(35, 119, 70));
        g.draw(guirlande);
        for (double ornamentX : new double[]{0.34, 0.5, 0.66}) {
            double ornamentY = ornamentX == 0.5 ? 0.55 : 0.52;
            g.setColor(ornamentX == 0.5 ? new Color(255, 211, 110) : new Color(196, 43, 54));
            double d = s * 0.09;
            g.fillOval((int) (x + s * ornamentX - d / 2),
                    (int) (y + s * ornamentY - d / 2), (int) d, (int) d);
        }
        g.dispose();
    }

    private static void dessinerMitra(Graphics2D graphics, int x, int y, int taille) {
        Graphics2D g = (Graphics2D) graphics.create();
        double s = taille;
        g.setColor(new Color(191, 44, 54));
        g.fillRoundRect((int) (x + s * 0.43), (int) (y + s * 0.16),
                (int) (s * 0.23), Math.max(2, (int) (s * 0.055)),
                Math.max(2, (int) (s * 0.025)), Math.max(2, (int) (s * 0.025)));
        g.setColor(new Color(255, 211, 110));
        g.fillOval((int) (x + s * 0.52), (int) (y + s * 0.11),
                Math.max(3, (int) (s * 0.065)), Math.max(3, (int) (s * 0.065)));
        g.dispose();
    }

    private static void dessinerEcharpe(Graphics2D graphics, int x, int y, int taille) {
        Graphics2D g = (Graphics2D) graphics.create();
        double s = taille;
        Path2D.Double echarpe = new Path2D.Double();
        echarpe.moveTo(x + s * 0.24, y + s * 0.59);
        echarpe.curveTo(x + s * 0.38, y + s * 0.65, x + s * 0.57, y + s * 0.65,
                x + s * 0.73, y + s * 0.57);
        g.setColor(new Color(193, 42, 53));
        g.setStroke(new BasicStroke((float) (s * 0.09), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(echarpe);
        g.setStroke(new BasicStroke((float) (s * 0.07), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.drawLine((int) (x + s * 0.65), (int) (y + s * 0.59),
                (int) (x + s * 0.7), (int) (y + s * 0.76));
        g.dispose();
    }

    private static void dessinerFlocon(Graphics2D graphics, double centreX, double centreY, double rayon) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setColor(new Color(35, 112, 67));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int angle = 0; angle < 180; angle += 60) {
            double radians = Math.toRadians(angle);
            int dx = (int) Math.round(Math.cos(radians) * rayon);
            int dy = (int) Math.round(Math.sin(radians) * rayon);
            g.drawLine((int) centreX - dx, (int) centreY - dy,
                    (int) centreX + dx, (int) centreY + dy);
        }
        g.dispose();
    }

    private static void dessinerHoux(Graphics2D graphics, double x, double y, double taille) {
        Graphics2D g = (Graphics2D) graphics.create();
        g.setColor(new Color(38, 118, 68));
        g.fillOval((int) x, (int) (y + taille * 0.3), (int) (taille * 0.7), (int) (taille * 0.42));
        g.fillOval((int) (x + taille * 0.3), (int) y, (int) (taille * 0.7), (int) (taille * 0.42));
        g.setColor(new Color(202, 39, 49));
        g.fillOval((int) (x + taille * 0.29), (int) (y + taille * 0.28),
                Math.max(3, (int) (taille * 0.28)), Math.max(3, (int) (taille * 0.28)));
        g.dispose();
    }

    private static final class DeplacementAnime {
        private final Piece piece;
        private final int ligneDepart;
        private final int colonneDepart;
        private final int ligneArrivee;
        private final int colonneArrivee;
        private final long debut;
        private final long duree;

        private DeplacementAnime(Piece piece, int ligneDepart, int colonneDepart,
                                 int ligneArrivee, int colonneArrivee, long debut, long duree) {
            this.piece = piece;
            this.ligneDepart = ligneDepart;
            this.colonneDepart = colonneDepart;
            this.ligneArrivee = ligneArrivee;
            this.colonneArrivee = colonneArrivee;
            this.debut = debut;
            this.duree = duree;
        }

        private double progression(long maintenant) {
            return Math.min(1.0, (double) (maintenant - debut) / duree);
        }
    }

    private static final class Coup {
        private final int ligneDepart;
        private final int colonneDepart;
        private final int ligneArrivee;
        private final int colonneArrivee;
        private final Piece.Type promotion;
        private final boolean enPassant;
        private final boolean roque;

        private Coup(int ligneDepart, int colonneDepart, int ligneArrivee, int colonneArrivee) {
            this(ligneDepart, colonneDepart, ligneArrivee, colonneArrivee, null, false, false);
        }

        private Coup(int ligneDepart, int colonneDepart, int ligneArrivee, int colonneArrivee,
                     Piece.Type promotion, boolean enPassant, boolean roque) {
            this.ligneDepart = ligneDepart;
            this.colonneDepart = colonneDepart;
            this.ligneArrivee = ligneArrivee;
            this.colonneArrivee = colonneArrivee;
            this.promotion = promotion;
            this.enPassant = enPassant;
            this.roque = roque;
        }
    }
}
