package jeuDeDame;

/**
 * Modèle minimal d'un pion de dames.
 *
 * Une pièce possède une couleur immuable et un état supplémentaire appelé dame.
 * Le drapeau dame change ses déplacements futurs, mais pas sa couleur ni son identité.
 * Les simulations utilisent copie() pour conserver l'immuabilité de l'état testé.
 */
public final class Piece {
    public static final int BLANC = 1;
    public static final int NOIR = 2;

    private final int couleur;
    private boolean dame;

    public Piece(int couleur) {
        // Refuse les entiers arbitraires pour que chaque Piece ait une couleur connue.
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
        dame = true; // Transforme un pion ordinaire en dame.
    }

    Piece copie() {
        return new Piece(couleur, dame); // Conserve couleur et état sans partager l'objet.
    }
}
