package nz.jordy.donutsmp;
 
import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;
 
public class StreamManagerClient implements ClientModInitializer {
    private static KeyMapping openMenuKey;
 
    private static boolean hudEnabled = true;
    private static long timerEndsAt = 0L;
    private static String latestNotification = "Waiting for chat...";
    private static long notificationReceivedAt = 0L;
 
    @Override
    public void onInitializeClient() {
        openMenuKey = KeyMappingHelper.registerKeyMapping(new KeyMapping(
                "key.donutsmp_stream_manager.open",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC
        ));
 
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openMenuKey.consumeClick()) {
                client.setScreen(new ManagerScreen());
            }
 
            if (timerEndsAt > 0L && System.currentTimeMillis() >= timerEndsAt) {
                timerEndsAt = 0L;
                latestNotification = "Timer finished!";
                notificationReceivedAt = System.currentTimeMillis();
            }
        });
 
        // Show ordinary incoming player chat in the HUD notification line.
        ClientReceiveMessageEvents.CHAT.register(
                (message, signedMessage, sender, boundChatType, timestamp) ->
                        setNotification(message.getString())
        );
 
        // Also show normal server messages, but not action-bar messages.
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> {
            if (!overlay) {
                setNotification(message.getString());
            }
        });
 
        HudElementRegistry.addLast(
                Identifier.fromNamespaceAndPath("donutsmp_stream_manager", "stream_manager_hud"),
                StreamManagerClient::renderHud
        );
    }
 
    private static void setNotification(String message) {
        if (message == null || message.isBlank()) {
            return;
        }
 
        latestNotification = message.replaceAll("§[0-9a-fk-or]", "").trim();
        notificationReceivedAt = System.currentTimeMillis();
    }
 
    private static void startTimer(int seconds) {
        timerEndsAt = System.currentTimeMillis() + (seconds * 1000L);
        latestNotification = "Timer started: " + seconds + " seconds";
        notificationReceivedAt = System.currentTimeMillis();
    }
 
    private static String getTimerText() {
        if (timerEndsAt <= 0L) {
            return "Timer: stopped";
        }
 
        long remaining = Math.max(0L, (timerEndsAt - System.currentTimeMillis() + 999L) / 1000L);
        long minutes = remaining / 60L;
        long seconds = remaining % 60L;
        return String.format("Timer: %02d:%02d", minutes, seconds);
    }
 
    private static void renderHud(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
 
        if (!hudEnabled || minecraft.player == null) {
            return;
        }
 
        int x = 8;
        int y = 8;
        int width = 260;
        int height = 48;
 
        graphics.fill(x - 4, y - 4, x + width, y + height, 0x99000000);
        graphics.text(minecraft.font, "STREAM MANAGER", x, y, 0xFFFFAA00, true);
        graphics.text(minecraft.font, getTimerText(), x, y + 13, 0xFFFFFFFF, true);
 
        String notice = latestNotification;
        if (notice.length() > 38) {
            notice = notice.substring(0, 35) + "...";
        }
        graphics.text(minecraft.font, "Chat: " + notice, x, y + 27, 0xFFE0E0E0, true);
    }
 
    private static class ManagerScreen extends Screen {
        protected ManagerScreen() {
            super(Component.literal("Stream Manager"));
        }
 
        @Override
        protected void init() {
            int centerX = this.width / 2;
            int startY = this.height / 2 - 55;
 
            this.addRenderableWidget(Button.builder(
                    Component.literal("Start 30 second timer"),
                    button -> startTimer(30)
            ).bounds(centerX - 100, startY, 200, 20).build());
 
            this.addRenderableWidget(Button.builder(
                    Component.literal("Start 1 minute timer"),
                    button -> startTimer(60)
            ).bounds(centerX - 100, startY + 26, 200, 20).build());
 
            this.addRenderableWidget(Button.builder(
                    Component.literal("Start 5 minute timer"),
                    button -> startTimer(300)
            ).bounds(centerX - 100, startY + 52, 200, 20).build());
 
            this.addRenderableWidget(Button.builder(
                    Component.literal("Stop timer"),
                    button -> timerEndsAt = 0L
            ).bounds(centerX - 100, startY + 78, 95, 20).build());
 
            this.addRenderableWidget(Button.builder(
                    Component.literal(hudEnabled ? "HUD: ON" : "HUD: OFF"),
                    button -> {
                        hudEnabled = !hudEnabled;
                        button.setMessage(Component.literal(hudEnabled ? "HUD: ON" : "HUD: OFF"));
                    }
            ).bounds(centerX + 5, startY + 78, 95, 20).build());
 
            this.addRenderableWidget(Button.builder(
                    Component.literal("Close"),
                    button -> this.onClose()
            ).bounds(centerX - 50, startY + 112, 100, 20).build());
        }
 
        @Override
        public void extractRenderState(
                GuiGraphicsExtractor graphics,
                int mouseX,
                int mouseY,
                float delta
        ) {
            super.extractRenderState(graphics, mouseX, mouseY, delta);
 
            int centerX = this.width / 2;
            int startY = this.height / 2 - 55;
 
            graphics.text(this.font, this.title, centerX - this.font.width(this.title) / 2,
                    24, 0xFFFFFFFF, true);
 
            graphics.text(this.font, getTimerText(), centerX - 100,
                    this.height / 2 - 75, 0xFFFFFF55, true);
 
            String notice = latestNotification;
            if (notice.length() > 52) {
                notice = notice.substring(0, 49) + "...";
            }
            graphics.text(this.font, "Latest chat: " + notice, centerX - 100,
                    startY + 140, 0xFFE0E0E0, true);
        }
 
        @Override
        public void onClose() {
            this.minecraft.setScreen(null);
        }
 
        @Override
        public boolean isPauseScreen() {
            return false;
        }
    }
}
 
