# EURO-OTC-AI

Assistant Android flottant d'observation de graphiques OTC.

## État actuel — prototype 0.1.0
- Application Android native Kotlin.
- Demande explicite de l'autorisation MediaProjection.
- Service au premier plan pour maintenir la session de capture.
- Bulle superposée affichant NO SIGNAL.
- Compilation APK debug par GitHub Actions.

## Limites à ce stade
Cette version établit le socle Android et le pipeline de capture. Elle ne reconnaît pas encore les bougies, ne reconstruit pas les OHLC et ne génère volontairement aucun BUY/SELL. La capture d'écran ne donne pas accès au flux OTC interne de Pocket Option. Un signal ne sera activé qu'après implémentation et validation de la lecture visuelle, de la synchronisation multi-timeframe et de tests statistiques hors échantillon.

Aucun ordre n'est envoyé automatiquement.
