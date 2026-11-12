package nz.jordy.donutsmp;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StreamManagerClient implements ClientModInitializer {
    static final Gson GSON = new Gson();
    static final Path DATA = MinecraftClient.getInstance().runDirectory.toPath()
            .resolve("config/donutsmp-stream-manager.json");
    static final Map<String, Long> paidTotals = new LinkedHashMap<>();
    static final List<Bid> bids = new ArrayList<>();
    static String lastPaid = "None detected yet";
    static String auctionItem = "Not set";
    static long highestBid = 0;
    static String highestBidder = "—";
    static long endAt = 0;
    static boolean auctionRunning = false;
    static boolean hudEnabled = true;
    static KeyBinding openKey;
    static final Pattern PAYMENT = Pattern.compile("(?i)([A-Za-z0-9_]{1,16})\\s+paid you\\s+\\$?([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmb]?)");
    static final Pattern PAYMENT_ALT = Pattern.compile("(?i)you (?:received|got)\\s+\\$?([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmb]?)\\s+from\\s+([A-Za-z0-9_]{1,16})");
    static final Pattern BID = Pattern.compile("(?i)(?:^|\\s)([A-Za-z0-9_]{1,16})?[: ]+\\$?([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmb])(?:\\s+bid)?\\s*$");

    @Override public void onInitializeClient() {
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.donutsmp_stream_manager.open", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyBinding.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openKey.wasPressed()) client.setScreen(new ManagerScreen());
            if (auctionRunning && System.currentTimeMillis() >= endAt) auctionRunning = false;
        });
        ClientReceiveMessageEvents.GAME.register((message, overlay) -> handleChat(message.getString()));
        ClientReceiveMessageEvents.CHAT.register((message, signedMessage, sender, params, timestamp) ->
                handleChat(message.getString()));
        HudRenderCallback.EVENT.register((draw, tickCounter) -> renderHud(draw));
        load();
    }

    static void handleChat(String raw) {
        String line = raw.replaceAll("§[0-9a-fk-or]", "").trim();
        Matcher p = PAYMENT.matcher(line);
        if (p.find()) { recordPayment(p.group(1), parseMoney(p.group(2))); return; }
        Matcher a = PAYMENT_ALT.matcher(line);
        if (a.find()) { recordPayment(a.group(2), parseMoney(a.group(1))); return; }
        if (auctionRunning) {
            Matcher b = BID.matcher(line);
            if (b.find()) {
                String name = b.group(1);
                long amount = parseMoney(b.group(2));
                if (name != null && !name.isBlank() && amount > highestBid) {
                    highestBid = amount; highestBidder = name; bids.add(new Bid(name, amount));
                }
            }
        }
    }

    static void recordPayment(String player, long amount) {
        if (player == null || player.isBlank() || amount <= 0) return;
        lastPaid = player + " — $" + amount;
        paidTotals.merge(player, amount, Long::sum);
        save();
    }

    static long parseMoney(String input) {
        try {
            String s = input.toLowerCase(Locale.ROOT).replace("$","").replace(",","").replaceAll("\\s","");
            double mult = 1;
            if (s.endsWith("k")) { mult=1_000; s=s.substring(0,s.length()-1); }
            else if (s.endsWith("m")) { mult=1_000_000; s=s.substring(0,s.length()-1); }
            else if (s.endsWith("b")) { mult=1_000_000_000; s=s.substring(0,s.length()-1); }
            return (long)(Double.parseDouble(s)*mult);
        } catch (Exception e) { return 0; }
    }

    static void renderHud(DrawContext draw) {
        if (!hudEnabled) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options.hudHidden) return;
        int x = 8, y = 8;
        draw.fill(x-4,y-4,x+206,y+32,0x99000000);
        draw.drawText(mc.textRenderer, "§6STREAM MANAGER", x,y,0xFFFFFF,false);
        draw.drawText(mc.textRenderer, "Last paid: " + lastPaid, x,y+12,0xFFFFFF,false);
    }

    static void save() {
        try {
            Files.createDirectories(DATA.getParent());
            Files.writeString(DATA, GSON.toJson(paidTotals));
        } catch (IOException ignored) {}
    }
    static void load() {
        try {
            if (Files.exists(DATA)) {
                Type type = new TypeToken<LinkedHashMap<String,Long>>(){}.getType();
                Map<String,Long> loaded = GSON.fromJson(Files.readString(DATA), type);
                if (loaded != null) paidTotals.putAll(loaded);
            }
        } catch (Exception ignored) {}
    }

    record Bid(String player, long amount) {}

    static class ManagerScreen extends Screen {
        TextFieldWidget itemField, secondsField, bidderField, amountField;
        ManagerScreen() { super(Text.literal("DonutSMP Stream Manager")); }
        @Override protected void init() {
            int cx = width/2, y = 38;
            itemField = new TextFieldWidget(textRenderer,cx-145,y,290,20,Text.literal("Auction item"));
            itemField.setText(auctionItem.equals("Not set") ? "" : auctionItem); addDrawableChild(itemField);
            secondsField = new TextFieldWidget(textRenderer,cx-145,y+48,90,20,Text.literal("Seconds"));
            secondsField.setText("60"); addDrawableChild(secondsField);
            addDrawableChild(ButtonWidget.builder(Text.literal("Start / reset timer"), b -> {
                long seconds = Math.max(1, Math.min(3600, parseMoney(secondsField.getText())));
                auctionItem = itemField.getText().isBlank() ? "Unspecified item" : itemField.getText();
                bids.clear(); highestBid=0; highestBidder="—";
                endAt=System.currentTimeMillis()+seconds*1000; auctionRunning=true;
            }).dimensions(cx-45,y+48,190,20).build());
            bidderField = new TextFieldWidget(textRenderer,cx-145,y+86,135,20,Text.literal("Bidder")); addDrawableChild(bidderField);
            amountField = new TextFieldWidget(textRenderer,cx+5,y+86,135,20,Text.literal("Amount")); addDrawableChild(amountField);
            addDrawableChild(ButtonWidget.builder(Text.literal("Add bid manually"), b -> {
                long amount=parseMoney(amountField.getText()); String name=bidderField.getText().trim();
                if (!name.isEmpty() && amount>highestBid) { highestBid=amount; highestBidder=name; bids.add(new Bid(name,amount)); }
                bidderField.setText(""); amountField.setText("");
            }).dimensions(cx-75,y+112,150,20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("End auction"), b -> auctionRunning=false)
                    .dimensions(cx-145,y+145,135,20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("HUD ON/OFF"), b -> hudEnabled=!hudEnabled)
                    .dimensions(cx+10,y+145,135,20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal("Close"), b -> close())
                    .dimensions(cx-45,height-30,90,20).build());
        }
        @Override public void render(DrawContext draw,int mouseX,int mouseY,float delta) {
            renderBackground(draw,mouseX,mouseY,delta);
            super.render(draw,mouseX,mouseY,delta);
            int cx=width/2, y=12;
            draw.drawCenteredTextWithShadow(textRenderer,title,cx,y,0xFFFFFF);
            draw.drawText(textRenderer,"Last payment: "+lastPaid, cx-145, height-75,0xFFFFFF,false);
            draw.drawText(textRenderer,"Auction: "+auctionItem,cx-145, height-62,0xFFFFFF,false);
            draw.drawText(textRenderer,"Highest: "+highestBidder+" — $"+highestBid,cx-145,height-49,0x55FF55,false);
            String timer=auctionRunning ? Math.max(0,(endAt-System.currentTimeMillis()+999)/1000)+"s remaining" : "Timer stopped";
            draw.drawText(textRenderer,timer,cx-145,height-36,0xFFFF55,false);
            int by=height-100;
            draw.drawText(textRenderer,"Recent bids:",cx+35,by,0xFFFFFF,false);
            for(int i=Math.max(0,bids.size()-4);i<bids.size();i++) {
                Bid bid=bids.get(i); by+=12;
                draw.drawText(textRenderer,bid.player()+"  $"+bid.amount(),cx+35,by,0xDDDDDD,false);
            }
        }
        @Override public boolean shouldPause(){ return false; }
    }
}
