package com.serverbot.commands.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Represents an active blackjack game session for a user.
 * Tracks game state, cards, bets, and provides card utility methods.
 */
public class BlackjackGame {
    
    private static final Random RANDOM = new Random();
    
    // Active games keyed by gameId
    private static final Map<String, BlackjackGame> ACTIVE_GAMES = new ConcurrentHashMap<>();
    // Map userId -> gameId for quick lookup
    private static final Map<String, String> USER_GAMES = new ConcurrentHashMap<>();
    
    // Auto-expire games after 2 minutes of inactivity
    private static final ScheduledExecutorService CLEANUP_EXECUTOR = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "blackjack-cleanup");
        t.setDaemon(true);
        return t;
    });
    
    private final String gameId;
    private final String userId;
    private final String guildId;
    private final long betAmount;
    private final long originalBalance;
    private final List<Integer> playerCards;
    private final List<Integer> dealerCards;
    private boolean doubledDown;
    private long createdAt;
    
    public BlackjackGame(String userId, String guildId, long betAmount, long originalBalance) {
        this.gameId = UUID.randomUUID().toString().substring(0, 8);
        this.userId = userId;
        this.guildId = guildId;
        this.betAmount = betAmount;
        this.originalBalance = originalBalance;
        this.playerCards = new ArrayList<>();
        this.dealerCards = new ArrayList<>();
        this.doubledDown = false;
        this.createdAt = System.currentTimeMillis();
        
        // Register this game
        ACTIVE_GAMES.put(gameId, this);
        USER_GAMES.put(userId, gameId);
        
        // Schedule auto-expiry after 2 minutes
        CLEANUP_EXECUTOR.schedule(() -> {
            if (ACTIVE_GAMES.containsKey(gameId)) {
                removeGame();
            }
        }, 2, TimeUnit.MINUTES);
    }
    
    /**
     * Deal the initial 4 cards (2 to player, 2 to dealer)
     */
    public void dealInitialCards() {
        playerCards.add(drawCard());
        dealerCards.add(drawCard());
        playerCards.add(drawCard());
        dealerCards.add(drawCard());
    }
    
    /**
     * Draw a card (1-10, with face cards as 10, ace as 1)
     */
    public static int drawCard() {
        int card = RANDOM.nextInt(13) + 1;
        return Math.min(card, 10);
    }
    
    /**
     * Player hits - draw a card
     * @return the drawn card value
     */
    public int playerHit() {
        int card = drawCard();
        playerCards.add(card);
        return card;
    }
    
    /**
     * Run the dealer's turn (hits on 16 or less, stands on 17+)
     */
    public void dealerPlay() {
        while (getDealerTotal() < 17) {
            dealerCards.add(drawCard());
        }
    }
    
    /**
     * Calculate the hand value with ace handling
     */
    public static int calculateHandValue(List<Integer> cards) {
        int total = 0;
        int aces = 0;
        
        for (int card : cards) {
            if (card == 1) {
                aces++;
                total += 11;
            } else {
                total += card;
            }
        }
        
        while (total > 21 && aces > 0) {
            total -= 10;
            aces--;
        }
        
        return total;
    }
    
    public int getPlayerTotal() {
        return calculateHandValue(playerCards);
    }
    
    public int getDealerTotal() {
        return calculateHandValue(dealerCards);
    }
    
    /**
     * Format a single card value for display
     */
    public String formatSingleCard(int card) {
        return switch (card) {
            case 1 -> "A";
            case 10 -> "10";
            default -> String.valueOf(card);
        };
    }
    
    /**
     * Format the player's cards for display
     */
    public String formatPlayerCards() {
        return formatCardList(playerCards);
    }
    
    /**
     * Format the dealer's cards for display.
     * @param showAll if true, show all cards; if false, show first card + "?"
     */
    public String formatDealerCards(boolean showAll) {
        if (showAll) {
            return formatCardList(dealerCards);
        }
        return formatSingleCard(dealerCards.get(0)) + ", ?";
    }
    
    private String formatCardList(List<Integer> cards) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cards.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(formatSingleCard(cards.get(i)));
        }
        return sb.toString();
    }
    
    // Getters
    public String getGameId() { return gameId; }
    public String getUserId() { return userId; }
    public String getGuildId() { return guildId; }
    public long getBetAmount() { return betAmount; }
    public long getOriginalBalance() { return originalBalance; }
    public List<Integer> getPlayerCards() { return playerCards; }
    public List<Integer> getDealerCards() { return dealerCards; }
    public boolean isDoubledDown() { return doubledDown; }
    public void setDoubledDown(boolean doubledDown) { this.doubledDown = doubledDown; }
    
    /**
     * Get the effective bet (doubled if double-down was used)
     */
    public long getEffectiveBet() {
        return doubledDown ? betAmount * 2 : betAmount;
    }
    
    // Static lookup methods
    public static boolean hasActiveGame(String userId) {
        return USER_GAMES.containsKey(userId);
    }
    
    public static BlackjackGame getGame(String gameId) {
        return ACTIVE_GAMES.get(gameId);
    }
    
    public static BlackjackGame getGameByUserId(String userId) {
        String gameId = USER_GAMES.get(userId);
        return gameId != null ? ACTIVE_GAMES.get(gameId) : null;
    }
    
    /**
     * Remove the game from active tracking
     */
    public void removeGame() {
        ACTIVE_GAMES.remove(gameId);
        USER_GAMES.remove(userId);
    }
}
