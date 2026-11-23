package jeuDeDame;

public final class Piece {
    public static final int BLANC = 1;
    public static final int NOIR = 2;

    private final int couleur;
    private boolean dame;

    public Piece(int couleur) {
        if (couleur != BLANC && couleur != NOIR) {
            throw new IllegalArgumentException("La couleur doit être BLANC ou NOIR.");
        }
        this.couleur = couleur;
    }

    private Piece(int couleur, boolean dame) {
        this(couleur);
        this.dame = dame;
    }

    public int getCouleur() {
        return couleur;
    }

    public boolean estDame() {
        return dame;
    }

    public void promouvoir() {
        dame = true;
    }

    Piece copie() {
        return new Piece(couleur, dame);
    }
}
