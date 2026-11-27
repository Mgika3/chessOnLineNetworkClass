package jeuEchec;

/**
 * Modélise une pièce d'échecs sans connaître le plateau.
 *
 * La couleur est immuable, le type peut changer lors d'une promotion et
 * aBouge sert notamment à autoriser le roque. Une copie indépendante est utilisée
 * dans les simulations : l'algorithme peut tester un coup sans modifier la vraie pièce.
 */
public final class Piece {
    public enum Couleur {
        BLANC, NOIR;

        public Couleur adverse() {
            // Il n'existe que deux couleurs : renvoyer l'autre est donc sans ambiguite.
            return this == BLANC ? NOIR : BLANC;
        }
    }

    public enum Type {
        ROI, DAME, TOUR, FOU, CAVALIER, PION
    }

    private final Couleur couleur;
    private Type type;
    private boolean aBouge;

    public Piece(Couleur couleur, Type type) {
        if (couleur == null || type == null) {
            throw new IllegalArgumentException("La couleur et le type de pièce sont requis.");
        }
        this.couleur = couleur;
        this.type = type;
    }

    public Couleur getCouleur() {
        return couleur;
    }

    public Type getType() {
        return type;
    }

    public boolean aBouge() {
        return aBouge;
    }

    void marquerCommeDeplacee() {
        aBouge = true; // Désactive ensuite les déplacements initiaux réservés à une pièce immobile.
    }

    void promouvoir(Type nouveauType) {
        // La promotion est une transition d'etat reservee au pion ; le roi est interdit.
        if (type != Type.PION || nouveauType == null
                || nouveauType == Type.PION || nouveauType == Type.ROI) {
            throw new IllegalArgumentException("Promotion de pion invalide.");
        }
        type = nouveauType;
        aBouge = true;
    }

    Piece copie() {
        Piece copie = new Piece(couleur, type); // Conserve l'identité de couleur et de type.
        copie.aBouge = aBouge; // Conserve aussi l'historique nécessaire aux simulations.
        return copie; // Objet distinct : les modifications ultérieures restent locales.
    }

    public String symbole() {
        switch (type) {
            case ROI:
                return couleur == Couleur.BLANC ? "\u2654" : "\u265A";
            case DAME:
                return couleur == Couleur.BLANC ? "\u2655" : "\u265B";
            case TOUR:
                return couleur == Couleur.BLANC ? "\u2656" : "\u265C";
            case FOU:
                return couleur == Couleur.BLANC ? "\u2657" : "\u265D";
            case CAVALIER:
                return couleur == Couleur.BLANC ? "\u2658" : "\u265E";
            case PION:
                return couleur == Couleur.BLANC ? "\u2659" : "\u265F";
            default:
                throw new IllegalStateException("Type de pièce inconnu.");
        }
    }
}
