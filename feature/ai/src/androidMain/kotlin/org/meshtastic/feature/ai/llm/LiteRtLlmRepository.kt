/*
 * Copyright (c) 2026 Chris7X
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package org.meshtastic.feature.ai.llm

import android.content.Context
import kotlin.random.Random
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

private const val MAX_SUGGESTIONS = 3

class LiteRtLlmRepository(
    private val context: Context
) : LlmRepository {

    override val isModelAvailable: Flow<Boolean> = flow { emit(false) }

    override suspend fun generateResponse(prompt: String, maxTokens: Int): Flow<String> = flow {
        // Fallback: return empty flow when model not available
    }

    override suspend fun generateSmartReplies(
        conversationContext: ConversationContext,
        maxSuggestions: Int,
        languageCode: String
    ): List<String> {
        val lastMessage = conversationContext.recentMessages.lastOrNull()?.text ?: return emptyList()
        return getTemplateFallback(lastMessage, languageCode).take(maxSuggestions)
    }

    override suspend fun close() {
        // No resources to release in fallback implementation
    }

    private fun getTemplateFallback(message: String, languageCode: String): List<String> {
        val lower = message.lowercase().trim()
        // Deterministic seed: same message -> same 3 replies in the session,
        // different messages -> different order from pool of 6 -> perceived variety.
        val rng = Random(lower.hashCode().toLong())

        val pool: List<String> = when {
            lower.containsAny("aiuto", "help", "sos", "emergenza", "emergency",
                "pericolo", "danger", "mayday") -> when (languageCode) {
                "it" -> listOf("Arrivo subito!", "Dove sei?", "Chiamo aiuto",
                               "Sto venendo!", "Sei al sicuro?", "Manda la posizione!")
                "de" -> listOf("Komme sofort!", "Wo bist du?", "Rufe Hilfe",
                               "Ich komme!", "Bist du sicher?", "Sende Position!")
                "es" -> listOf("¡Voy!", "¿Dónde estás?", "Pido ayuda",
                               "¡Ya voy!", "¿Estás bien?", "¡Manda ubicación!")
                "fr" -> listOf("J'arrive!", "Où es-tu?", "J'appelle du secours",
                               "Je viens!", "Tu es en sécurité?", "Envoie ta position!")
                else -> listOf("On my way!", "Where are you?", "Getting help",
                               "Coming now!", "Are you safe?", "Send location!")
            }
            lower.containsAny("ciao", "hello", "hi", "hey", "salve", "buongiorno",
                "buonasera", "hola", "salut", "hallo") -> when (languageCode) {
                "it" -> listOf("Ciao!", "Come stai?", "Tutto bene?",
                               "Ehi!", "Buongiorno!", "Ci sei?")
                "de" -> listOf("Hallo!", "Wie geht's?", "Alles gut?",
                               "Hey!", "Guten Tag!", "Bist du da?")
                "es" -> listOf("¡Hola!", "¿Cómo estás?", "¿Todo bien?",
                               "¡Ey!", "¡Buenos días!", "¿Estás ahí?")
                "fr" -> listOf("Salut!", "Ça va?", "Tout va bien?",
                               "Eh!", "Bonjour!", "T'es là?")
                else -> listOf("Hi!", "How are you?", "All good?",
                               "Hey!", "Good morning!", "You there?")
            }
            lower.containsAny("addio", "bye", "arrivederci", "a dopo", "a presto",
                "see you", "tschüss", "au revoir") -> when (languageCode) {
                "it" -> listOf("A presto!", "Ciao!", "Buona giornata!",
                               "Ci vediamo!", "A dopo!", "Stai bene!")
                "de" -> listOf("Bis bald!", "Tschüss!", "Schönen Tag!",
                               "Auf Wiedersehen!", "Bis später!", "Pass auf dich auf!")
                "es" -> listOf("¡Hasta luego!", "¡Adiós!", "¡Buen día!",
                               "¡Hasta pronto!", "¡Nos vemos!", "¡Cuídate!")
                "fr" -> listOf("À bientôt!", "Salut!", "Bonne journée!",
                               "Au revoir!", "À plus!", "Prends soin de toi!")
                else -> listOf("See you!", "Bye!", "Take care!",
                               "Later!", "See you soon!", "Have a good one!")
            }
            lower.containsAny("come stai", "how are you", "tutto bene", "stai bene",
                "you ok", "wie geht", "ça va", "cómo estás") -> when (languageCode) {
                "it" -> listOf("Bene, grazie!", "Tutto ok", "E tu?",
                               "Sto bene!", "Abbastanza bene", "Non male!")
                "de" -> listOf("Gut, danke!", "Alles ok", "Und dir?",
                               "Mir geht's gut!", "Ganz gut", "Nicht schlecht!")
                "es" -> listOf("¡Bien, gracias!", "Todo ok", "¿Y tú?",
                               "¡Estoy bien!", "Bastante bien", "¡No mal!")
                "fr" -> listOf("Bien, merci!", "Tout va bien", "Et toi?",
                               "Ça va bien!", "Plutôt bien", "Pas mal!")
                else -> listOf("Good, thanks!", "All ok", "And you?",
                               "I'm fine!", "Pretty good", "Not bad!")
            }
            lower.containsAny("dove sei", "where are you", "posizione", "position",
                "location", "wo bist") -> when (languageCode) {
                "it" -> listOf("Sono qui", "In arrivo", "Ti mando la posizione",
                               "Quasi li'", "5 minuti", "Al punto di ritrovo")
                "de" -> listOf("Bin hier", "Komme gleich", "Sende Position",
                               "Fast da", "5 Minuten", "Am Treffpunkt")
                "es" -> listOf("Estoy aquí", "En camino", "Te mando ubicación",
                               "Casi ahí", "5 minutos", "En el punto de encuentro")
                "fr" -> listOf("Je suis là", "J'arrive", "J'envoie ma position",
                               "Presque là", "5 minutes", "Au point de rendez-vous")
                else -> listOf("I'm here", "On my way", "Sending location",
                               "Almost there", "5 minutes", "At the meetup point")
            }
            lower.contains("?") -> when (languageCode) {
                "it" -> listOf("Non so", "Fammi controllare", "Ti dico dopo",
                               "Non sono sicuro", "Ci penso", "Verifico subito")
                "de" -> listOf("Weiß nicht", "Muss prüfen", "Sage dir später",
                               "Nicht sicher", "Ich überlege", "Prüfe sofort")
                "es" -> listOf("No sé", "Déjame ver", "Te digo luego",
                               "No estoy seguro", "Lo pienso", "Verifico ahora")
                "fr" -> listOf("Je ne sais pas", "Je vérifie", "Je te dis après",
                               "Pas sûr", "J'y réfléchis", "Je vérifie tout de suite")
                else -> listOf("Not sure", "Let me check", "I'll tell you later",
                               "Not certain", "Thinking about it", "Checking now")
            }
            else -> when (languageCode) {
                "it" -> listOf("Ok!", "Ricevuto", "👍", "Capito", "Perfetto", "Roger")
                "de" -> listOf("Ok!", "Verstanden", "👍", "Alles klar", "Gut", "Roger")
                "es" -> listOf("¡Ok!", "Recibido", "👍", "Entendido", "Perfecto", "Roger")
                "fr" -> listOf("Ok!", "Reçu", "👍", "Compris", "Parfait", "Roger")
                else -> listOf("OK!", "Got it", "👍", "Understood", "Perfect", "Roger")
            }
        }

        // Pool da 6 varianti, seed deterministico -> 3 diverse per ogni messaggio
        return pool.shuffled(rng).take(MAX_SUGGESTIONS)
    }

    private fun String.containsAny(vararg words: String): Boolean =
        words.any { this.contains(it, ignoreCase = true) }
}
