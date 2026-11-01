package com.vylia.privacy.domain.security

/** Contrôles communs aux données venues de l'extérieur (sauvegarde, règles importées, base Exodus téléchargée). */
object Untrusted {
    private val PACKAGE = Regex("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z0-9_]+)*")
    private val PERMISSION = Regex("[A-Za-z0-9_.]+")

    /** Nom de paquet Android plausible. */
    fun isPackage(s: String) = s.length <= 255 && PACKAGE.matches(s)

    /** Nom de permission plausible. */
    fun isPermission(s: String) = s.length <= 200 && PERMISSION.matches(s)

    /**
     * Texte affichable : sans caractères de contrôle ni de mise en forme invisibles (dont l'inversion du sens
     * d'écriture, qui sert à maquiller un nom), espaces normalisés, longueur bornée.
     */
    fun text(s: String, max: Int): String {
        val visible = s.replace(WHITESPACE, " ").filter { c ->
            val type = Character.getType(c)
            type != Character.CONTROL.toInt() && type != Character.FORMAT.toInt() &&
                type != Character.UNASSIGNED.toInt() && type != Character.PRIVATE_USE.toInt()
        }
        return visible.replace(WHITESPACE, " ").trim().take(max).ifEmpty { "?" }
    }

    private val WHITESPACE = Regex("\\s+")
}
