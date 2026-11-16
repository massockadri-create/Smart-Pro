package com.example.security

enum class RoleCode(val code: String, val displayName: String, val description: String) {
    ADMIN("ADMIN", "Administrateur principal", "Contrôle total du magasin, utilisateurs, finances, validation et sauvegardes"),
    MANAGER("MANAGER", "Gestionnaire", "Gestion produits, stocks, fournisseurs, achats, inventaires et rapports autorisés"),
    CASHIER("CASHIER", "Caissier", "Point de vente, panier, tickets, gestion de sa caisse et consultation de ses opérations"),
    EMPLOYEE("EMPLOYEE", "Employé", "Accès ciblé aux tâches et opérations autorisées par l'administrateur")
}

enum class AppPermission(val code: String, val label: String, val category: String) {
    VIEW_SALES("VIEW_SALES", "Voir les ventes", "Ventes & POS"),
    CREATE_SALE("CREATE_SALE", "Créer une vente (POS)", "Ventes & POS"),
    MODIFY_SALE("MODIFY_SALE", "Modifier une vente", "Ventes & POS"),
    CANCEL_SALE("CANCEL_SALE", "Annuler une vente", "Ventes & POS"),

    VIEW_PRODUCTS("VIEW_PRODUCTS", "Voir les produits", "Produits"),
    CREATE_PRODUCT("CREATE_PRODUCT", "Créer un produit", "Produits"),
    MODIFY_PRODUCT("MODIFY_PRODUCT", "Modifier un produit / prix", "Produits"),
    DELETE_PRODUCT("DELETE_PRODUCT", "Supprimer un produit", "Produits"),

    VIEW_STOCK("VIEW_STOCK", "Voir les stocks", "Stocks"),
    MODIFY_STOCK("MODIFY_STOCK", "Modifier les stocks (entrées/sorties)", "Stocks"),

    PERFORM_INVENTORY("PERFORM_INVENTORY", "Effectuer un inventaire", "Inventaires"),
    VALIDATE_INVENTORY("VALIDATE_INVENTORY", "Valider un inventaire", "Inventaires"),

    VIEW_CASH_REGISTER("VIEW_CASH_REGISTER", "Voir la caisse", "Caisse"),
    OPEN_CASH_REGISTER("OPEN_CASH_REGISTER", "Ouvrir une caisse", "Caisse"),
    CLOSE_CASH_REGISTER("CLOSE_CASH_REGISTER", "Fermer une caisse", "Caisse"),

    VIEW_PURCHASES("VIEW_PURCHASES", "Voir les achats", "Achats & Fournisseurs"),
    MANAGE_PURCHASES("MANAGE_PURCHASES", "Gérer commandes & réceptions", "Achats & Fournisseurs"),
    MANAGE_SUPPLIERS("MANAGE_SUPPLIERS", "Gérer les fournisseurs", "Achats & Fournisseurs"),
    MANAGE_CUSTOMERS("MANAGE_CUSTOMERS", "Gérer les clients", "Clients"),

    VIEW_FINANCES("VIEW_FINANCES", "Voir les finances & dépenses", "Finances"),
    VIEW_PROFITS("VIEW_PROFITS", "Voir les bénéfices & marges", "Finances"),

    VIEW_REPORTS("VIEW_REPORTS", "Consulter les rapports", "Rapports"),
    EXPORT_REPORTS("EXPORT_REPORTS", "Exporter / imprimer rapports", "Rapports"),

    MANAGE_USERS("MANAGE_USERS", "Gérer les utilisateurs & permissions", "Administration"),
    MANAGE_SETTINGS("MANAGE_SETTINGS", "Gérer les paramètres du magasin", "Administration"),
    MANAGE_BACKUPS("MANAGE_BACKUPS", "Gérer les sauvegardes & synchro", "Administration"),
    RESET_DATA("RESET_DATA", "Réinitialiser les données (opérations / catalogue)", "Administration"),
    VIEW_AUDIT_LOGS("VIEW_AUDIT_LOGS", "Consulter le journal d'audit", "Administration");

    companion object {
        fun defaultPermissionsForRole(roleCode: String): Set<String> = when (roleCode) {
            RoleCode.ADMIN.code -> entries.map { it.code }.toSet()
            RoleCode.MANAGER.code -> setOf(
                VIEW_SALES.code, CREATE_SALE.code,
                VIEW_PRODUCTS.code, CREATE_PRODUCT.code, MODIFY_PRODUCT.code,
                VIEW_STOCK.code, MODIFY_STOCK.code,
                PERFORM_INVENTORY.code,
                VIEW_CASH_REGISTER.code,
                VIEW_PURCHASES.code, MANAGE_PURCHASES.code, MANAGE_SUPPLIERS.code,
                MANAGE_CUSTOMERS.code,
                VIEW_FINANCES.code,
                VIEW_REPORTS.code, EXPORT_REPORTS.code,
                RESET_DATA.code
            )
            RoleCode.CASHIER.code -> setOf(
                VIEW_SALES.code, CREATE_SALE.code,
                VIEW_PRODUCTS.code,
                VIEW_STOCK.code,
                VIEW_CASH_REGISTER.code, OPEN_CASH_REGISTER.code, CLOSE_CASH_REGISTER.code,
                MANAGE_CUSTOMERS.code
            )
            else -> setOf(
                VIEW_PRODUCTS.code,
                VIEW_STOCK.code,
                PERFORM_INVENTORY.code
            )
        }
    }
}
