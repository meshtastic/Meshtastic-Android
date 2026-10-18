# Flasher une radio pour la réinitialiser

Dernière mise à jour : 18 octobre 2026.

NetworkTact ne modifie pas le firmware : on installe le **firmware officiel Meshtastic**. Flasher
puis effacer la mémoire remet la radio comme neuve. **Toute la configuration de la radio est perdue**
(canaux, clés, région, nom, équipe, liste de nœuds). Prévoir de la refaire (§5).

> Cette procédure est la procédure générale Meshtastic. Les noms de boutons et les gestes de
> bootloader dépendent du modèle de radio : vérifier sur la page du modèle dans la documentation
> officielle avant de flasher. Je n'ai pas le modèle de votre radio ; le §3 couvre les plus courants.

## 1. Avant de commencer
- [ ] Noter le **modèle exact** de la radio (inscrit au dos ou à l'écran) : un firmware pour un autre
      modèle peut la bloquer.
- [ ] Sauvegarder : app Meshtastic ou NetworkTact → Réglages → Radio → Canaux → partager le QR des
      canaux ; noter nom long, région, préréglage LoRa, équipe `[Alpha]`.
- [ ] Batterie chargée ou radio branchée. Un **câble USB de données** (pas un câble de charge seule).
- [ ] Un ordinateur avec **Chrome ou Edge** (le Web Serial n'existe pas dans Firefox ni Safari).

## 2. Réinitialisation sans flasher (essayer d'abord)
1. Application → Réglages → Radio → Configuration de l'appareil → **Réinitialiser la base de nœuds** :
   efface seulement la liste des nœuds.
2. Même endroit → **Réinitialisation d'usine de l'appareil** (ou « de la configuration ») :
   efface la configuration, la radio redémarre. « Réinitialisation d'usine » complète efface aussi les
   canaux et les clés.
3. Si la radio répond encore, passer au §3.

## 3. Flasher par le flasheur web
1. Ouvrir **flasher.meshtastic.org** dans Chrome ou Edge.
2. Brancher la radio en USB. Choisir le **modèle** et la **version stable** du firmware.
3. Radio non détectée ? La mettre en mode bootloader :

| Famille | Geste |
|---|---|
| ESP32 (T-Beam, Heltec V3, T-Deck, Station G2…) | maintenir le bouton **BOOT / PRG**, brancher l'USB, relâcher |
| nRF52 (RAK4631, T-Echo, Heltec T114) | **double appui rapide** sur le bouton reset : un lecteur USB apparaît |

4. **Connecter** : choisir le port série dans la fenêtre du navigateur.
5. Choisir **« Tout effacer et installer » (Full erase and install)**, c'est cette option qui remet
   l'appareil à zéro. Éviter « Mise à jour » (garde la configuration).
6. nRF52 : le flasheur télécharge un fichier `.uf2` ; le copier sur le lecteur USB apparu.
7. Attendre la fin sans débrancher (1 à 3 minutes). La radio redémarre seule.

## 4. En ligne de commande (si le flasheur web échoue)
```
pip install esptool meshtastic
esptool.py --port <PORT> erase_flash
meshtastic --port <PORT> --factory-reset
```
Puis installer le firmware (`.bin` « factory » du modèle, depuis les versions officielles) avec
`esptool.py … write_flash 0x0 <fichier>.bin`. Pour nRF52, copier le `.uf2` sur le lecteur USB.

## 5. Reconfigurer pour NetworkTact
1. Appairer la radio en Bluetooth dans NetworkTact (code PIN affiché par la radio).
2. **Région** (ex. `EU_868`) puis préréglage LoRa (ex. `LONG_FAST`) : identiques sur toutes les radios,
   sinon elles ne s'entendent pas.
3. **Canaux** : scanner le QR sauvegardé ou recréer le canal commun.
4. **Nom long** : indicatif, ex. `ALPHA-1`. Puis choisir l'équipe dans NetworkTact : le suffixe
   `[Alpha]` est ajouté (39 octets au plus).
5. Position : activer le GPS et l'**horodatage de la position** (sans lui, « Diffuser ma position »
   est refusé).
6. Adopter la liste d'équipes (`[EQUIPES]` reçu du PC) et l'organigramme (QR du PC).
7. Vérifier : Diagnostic de liaison (écran §19), batterie, LoRa réel contre attendu.

## 6. Si ça ne marche pas
- **Port non détecté** : changer de câble (données), de port USB, refaire le geste bootloader.
- **Radio qui redémarre en boucle** après flash : refaire « Tout effacer et installer », vérifier que
  le modèle choisi est le bon.
- **nRF52 sans lecteur USB** : double appui plus rapide, ou débrancher/rebrancher en maintenant reset.
- **Ne s'entend plus avec les autres** : comparer région, préréglage, canal et clé avec une radio qui marche.
