package com.wrz.reading.data;

import com.wrz.reading.model.Emoji;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Emoji数据管理类
 * 将emoji数据从MyApplication中分离，避免Application类过大
 */
public class EmojiData {

    private static final List<Emoji> ALL_EMOJI_GROUPS = new ArrayList<>();
    private static final List<String> ALL_EMOJI_LIST = new ArrayList<>();
    private static boolean initialized = false;

    /**
     * 初始化所有emoji数据（懒加载）
     */
    public static synchronized void init() {
        if (initialized) return;
        initialized = true;

        ALL_EMOJI_GROUPS.add(getSmileys());
        ALL_EMOJI_GROUPS.add(getPeople());
        ALL_EMOJI_GROUPS.add(getNature());
        ALL_EMOJI_GROUPS.add(getFood());
        ALL_EMOJI_GROUPS.add(getActivities());
        ALL_EMOJI_GROUPS.add(getTravel());
        ALL_EMOJI_GROUPS.add(getObjects());
        ALL_EMOJI_GROUPS.add(getSymbols());

        // 收集所有emoji到一个列表
        for (Emoji emoji : ALL_EMOJI_GROUPS) {
            ALL_EMOJI_LIST.addAll(emoji.getList());
        }
    }

    /**
     * 获取所有emoji分组
     */
    public static List<Emoji> getAllGroups() {
        init();
        return ALL_EMOJI_GROUPS;
    }

    /**
     * 获取所有emoji列表
     */
    public static List<String> getAllEmojis() {
        init();
        return ALL_EMOJI_LIST;
    }

    /**
     * 根据索引获取emoji分组
     */
    public static Emoji getGroup(int index) {
        init();
        if (index >= 0 && index < ALL_EMOJI_GROUPS.size()) {
            return ALL_EMOJI_GROUPS.get(index);
        }
        return null;
    }

    /**
     * 获取分组数量
     */
    public static int getGroupCount() {
        init();
        return ALL_EMOJI_GROUPS.size();
    }

    /**
     * 获取随机emoji
     */
    public static String getRandomEmoji() {
        init();
        if (ALL_EMOJI_LIST.isEmpty()) {
            return "😀";
        }
        Random random = new Random();
        return ALL_EMOJI_LIST.get(random.nextInt(ALL_EMOJI_LIST.size()));
    }

    /**
     * 从指定分组获取随机emoji
     */
    public static String getRandomEmojiFromGroup(int groupIndex) {
        init();
        if (groupIndex >= 0 && groupIndex < ALL_EMOJI_GROUPS.size()) {
            List<String> groupList = ALL_EMOJI_GROUPS.get(groupIndex).getList();
            if (!groupList.isEmpty()) {
                Random random = new Random();
                return groupList.get(random.nextInt(groupList.size()));
            }
        }
        return "😀";
    }

    private static Emoji getSmileys() {
        String emoji =
                "\uD83C\uDDE8\uD83C\uDDF3\n" +
                "😀\n" + "😃\n" + "😄\n" + "😁\n" + "😆\n" + "😅\n" + "🤣\n" + "😂\n" +
                "🙂\n" + "🙃\n" + "🫠\n" + "😉\n" + "😊\n" + "😇\n" + "🥰\n" + "😍\n" +
                "🤩\n" + "😘\n" + "😗\n" + "😚\n" + "😙\n" + "🥲\n" + "😋\n" + "😛\n" +
                "😜\n" + "🤪\n" + "😝\n" + "🤑\n" + "🤗\n" + "🤭\n" + "🫢\n" + "🫣\n" +
                "🤫\n" + "🤔\n" + "🫡\n" + "🤐\n" + "🤨\n" + "😐\n" + "😑\n" + "😶\n" +
                "🫥\n" + "😶‍🌫️\n" + "😶‍🌫\n" + "😏\n" + "😒\n" + "🙄\n" + "😬\n" +
                "😮‍💨\n" + "🤥\n" + "🫨\n" + "😌\n" + "😔\n" + "😪\n" + "🤤\n" + "😴\n" +
                "🫩\n" + "😷\n" + "🤒\n" + "🤕\n" + "🤢\n" + "🤮\n" + "🤧\n" + "🥵\n" +
                "🥶\n" + "🥴\n" + "😵\n" + "😵‍💫\n" + "🤯\n" + "🤠\n" + "🥳\n" + "🥸\n" +
                "😎\n" + "🤓\n" + "🧐\n" + "😕\n" + "🫤\n" + "😟\n" + "🙁\n" + "😮\n" +
                "😯\n" + "😲\n" + "😳\n" + "🥺\n" + "🥹\n" + "😦\n" + "😧\n" + "😨\n" +
                "😰\n" + "😥\n" + "😢\n" + "😭\n" + "😱\n" + "😖\n" + "😣\n" + "😞\n" +
                "😓\n" + "😩\n" + "😫\n" + "🥱\n" + "😤\n" + "😡\n" + "😠\n" + "🤬\n";
        String[] strings = emoji.split("\n");
        return new Emoji("😀", Arrays.asList(strings));
    }

    private static Emoji getPeople() {
        String emoji =
                "😈\n" + "👿\n" + "💀\n" + "💩\n" + "🤡\n" + "👹\n" + "👺\n" + "👻\n" +
                "👽\n" + "👾\n" + "🤖\n" + "😺\n" + "😸\n" + "😹\n" + "😻\n" + "😼\n" +
                "😽\n" + "🙀\n" + "😿\n" + "😾\n" + "🙈\n" + "🙉\n" + "🙊\n" + "💌\n" +
                "❤\n" + "💘\n" + "💝\n" + "💖\n" + "💗\n" + "💓\n" + "💞\n" + "💕\n" +
                "💟\n" + "💔\n" + "🩷\n" + "🧡\n" + "💛\n" + "💚\n" + "💙\n" + "🩵\n" +
                "💜\n" + "🤎\n" + "🖤\n" + "🩶\n" + "🤍\n" + "💋\n" + "💯\n" + "💢\n" +
                "💥\n" + "💫\n" + "💦\n" + "💨\n" + "🕳️\n" + "💬\n" + "💭\n" + "💤\n";
        String[] strings = emoji.split("\n");
        return new Emoji("😈", Arrays.asList(strings));
    }

    private static Emoji getNature() {
        String emoji =
                "🧠\n" + "🫀\n" + "🫁\n" + "🦷\n" + "🦴\n" + "👀\n" + "👁️\n" + "👁\n" +
                "👅\n" + "👄\n" + "🫦\n" + "👶\n" + "🧒\n" + "👦\n" + "👧\n" + "🧑\n" +
                "👱\n" + "👨\n" + "🧔\n" + "👩\n" + "🧓\n" + "👴\n" + "👵\n" + "🙍\n" +
                "🙎\n" + "🙅\n" + "🙆\n" + "💁\n" + "🙋\n" + "🧏\n" + "🙇\n" + "🤦\n" +
                "🤷\n" + "👮\n" + "🕵️\n" + "💂\n" + "🥷\n" + "👷\n" + "🫅\n" + "🤴\n" +
                "👸\n" + "👳\n" + "👲\n" + "🧕\n" + "🤵\n" + "👰\n" + "🤰\n" + "🫃\n" +
                "🫄\n" + "🤱\n" + "👼\n" + "🎅\n" + "🤶\n" + "🦸\n" + "🦹\n" + "🧙\n" +
                "🧚\n" + "🧛\n" + "🧜\n" + "🧝\n" + "🧞\n" + "🧟\n" + "🧌\n" + "💆\n" +
                "💇\n" + "🚶\n" + "🧍\n" + "🧎\n" + "🏃\n" + "💃\n" + "🕺\n" + "👯\n" +
                "🧖\n" + "🧗\n" + "🤸\n" + "⛹️\n" + "🏋️\n" + "🚴\n" + "🚵\n" + "🤸\n" +
                "🤽\n" + "🤾\n" + "🤹\n" + "🧘\n" + "🛀\n" + "🛌\n";
        String[] strings = emoji.split("\n");
        return new Emoji("🧠", Arrays.asList(strings));
    }

    private static Emoji getFood() {
        String emoji =
                "🍇\n" + "🍈\n" + "🍉\n" + "🍊\n" + "🍋\n" + "🍌\n" + "🍍\n" + "🥭\n" +
                "🍎\n" + "🍏\n" + "🍐\n" + "🍑\n" + "🍒\n" + "🍓\n" + "🫐\n" + "🥝\n" +
                "🍅\n" + "🫒\n" + "🥥\n" + "🥑\n" + "🍆\n" + "🥔\n" + "🥕\n" + "🌽\n" +
                "🌶️\n" + "🫑\n" + "🥒\n" + "🥬\n" + "🥦\n" + "🧄\n" + "🧅\n" + "🍄\n" +
                "🥜\n" + "🫘\n" + "🌰\n" + "🍞\n" + "🥐\n" + "🥖\n" + "🫓\n" + "🥨\n" +
                "🥯\n" + "🥞\n" + "🧇\n" + "🧀\n" + "🍖\n" + "🍗\n" + "🥩\n" + "🥓\n" +
                "🍔\n" + "🍟\n" + "🍕\n" + "🌭\n" + "🥪\n" + "🌮\n" + "🌯\n" + "🫔\n" +
                "🥙\n" + "🧆\n" + "🥚\n" + "🍳\n" + "🥘\n" + "🍲\n" + "🫕\n" + "🥣\n" +
                "🥗\n" + "🍿\n" + "🧈\n" + "🧂\n" + "🥫\n" + "🍱\n" + "🍘\n" + "🍙\n" +
                "🍚\n" + "🍛\n" + "🍜\n" + "🍝\n" + "🍠\n" + "🍢\n" + "🍣\n" + "🍤\n" +
                "🍥\n" + "🥮\n" + "🍡\n" + "🥟\n" + "🥠\n" + "🥡\n";
        String[] strings = emoji.split("\n");
        return new Emoji("🍇", Arrays.asList(strings));
    }

    private static Emoji getActivities() {
        String emoji =
                "🦀\n" + "🦞\n" + "🦐\n" + "🦑\n" + "🦪\n" + "🍦\n" + "🍧\n" + "🍨\n" +
                "🍩\n" + "🍪\n" + "🎂\n" + "🍰\n" + "🧁\n" + "🥧\n" + "🍫\n" + "🍬\n" +
                "🍭\n" + "🍮\n" + "🍯\n" + "🍼\n" + "🥛\n" + "☕\n" + "🫖\n" + "🍵\n" +
                "🍶\n" + "🍾\n" + "🍷\n" + "🍸\n" + "🍹\n" + "🍺\n" + "🍻\n" + "🥂\n" +
                "🥃\n" + "🫗\n" + "🥤\n" + "🧋\n" + "🧃\n" + "🧉\n" + "🧊\n" + "🥢\n" +
                "🍽️\n" + "🍴\n" + "🥄\n" + "🔪\n" + "🫙\n" + "🏺\n";
        String[] strings = emoji.split("\n");
        return new Emoji("🦀", Arrays.asList(strings));
    }

    private static Emoji getTravel() {
        String emoji =
                "🌍\n" + "🌎\n" + "🌏\n" + "🗺️\n" + "🧭\n" + "🏔️\n" + "⛰️\n" + "🌋\n" +
                "🗻\n" + "🏕️\n" + "🏖️\n" + "🏜️\n" + "🏝️\n" + "🏞️\n" + "🏟️\n" + "🏛️\n" +
                "🏗️\n" + "🧱\n" + "🪨\n" + "🪵\n" + "🛖\n" + "🏘️\n" + "🏚️\n" + "🏠\n" +
                "🏡\n" + "🏢\n" + "🏣\n" + "🏤\n" + "🏥\n" + "🏦\n" + "🏨\n" + "🏩\n" +
                "🏪\n" + "🏫\n" + "🏬\n" + "🏭\n" + "🏯\n" + "🏰\n" + "💒\n" + "🗼\n" +
                "🗽\n" + "⛪\n" + "🕌\n" + "🛕\n" + "🕍\n" + "⛩️\n" + "🕋\n" + "⛲\n" +
                "⛺\n" + "🌁\n" + "🌃\n" + "🏙️\n" + "🌄\n" + "🌅\n" + "🌆\n" + "🌇\n" +
                "🌉\n" + "🌌\n" + "🎠\n" + "🛝\n" + "🎡\n" + "🎢\n" + "💈\n" + "🎪\n";
        String[] strings = emoji.split("\n");
        return new Emoji("🌍", Arrays.asList(strings));
    }

    private static Emoji getObjects() {
        String emoji =
                "🚂\n" + "🚃\n" + "🚄\n" + "🚅\n" + "🚆\n" + "🚇\n" + "🚈\n" + "🚉\n" +
                "🚊\n" + "🚝\n" + "🚞\n" + "🚋\n" + "🚌\n" + "🚍\n" + "🚎\n" + "🚐\n" +
                "🚑\n" + "🚒\n" + "🚓\n" + "🚔\n" + "🚕\n" + "🚖\n" + "🚗\n" + "🚘\n" +
                "🚙\n" + "🛻\n" + "🚚\n" + "🚛\n" + "🚜\n" + "🏎️\n" + "🏍️\n" + "🛵\n" +
                "🦽\n" + "🦼\n" + "🛺\n" + "🚲\n" + "🛴\n" + "🛹\n" + "🛼\n" + "🚏\n" +
                "🛣️\n" + "🛤️\n" + "🛞\n" + "⛽\n" + "🛞\n" + "🚨\n" + "🚥\n" + "🚦\n" +
                "🛑\n" + "🚧\n" + "⚓\n" + "🛟\n" + "⛵\n" + "🛶\n" + "🚤\n" + "🛳️\n" +
                "⛴️\n" + "🛥️\n" + "🚢\n" + "✈️\n" + "🛩️\n" + "🛫\n" + "🛬\n" + "🪂\n" +
                "💺\n" + "🚁\n" + "🚟\n" + "🚠\n" + "🚡\n" + "🛰️\n" + "🚀\n" + "🛸\n";
        String[] strings = emoji.split("\n");
        return new Emoji("🚂", Arrays.asList(strings));
    }

    private static Emoji getSymbols() {
        String emoji =
                "🎃\n" + "🎄\n" + "🎆\n" + "🎇\n" + "🧨\n" + "✨\n" + "🎈\n" + "🎉\n" +
                "🎊\n" + "🎋\n" + "🎍\n" + "🎎\n" + "🎏\n" + "🎐\n" + "🎑\n" + "🧧\n" +
                "🎀\n" + "🎁\n" + "🎗️\n" + "🎟️\n" + "🎖️\n" + "🏆\n" + "🏅\n" + "🥇\n" +
                "🥈\n" + "🥉\n" + "⚽\n" + "⚾\n" + "🥎\n" + "🏀\n" + "🏐\n" + "🏈\n" +
                "🏉\n" + "🎾\n" + "🥏\n" + "🎳\n" + "🏏\n" + "🏑\n" + "🏒\n" + "🥍\n" +
                "🏓\n" + "🏸\n" + "🥊\n" + "🥋\n" + "🥅\n" + "⛳\n" + "⛸️\n" + "🎣\n" +
                "🤿\n" + "🎽\n" + "🎿\n" + "🛷\n" + "🥌\n" + "🎯\n" + "🪀\n" + "🪁\n" +
                "🎱\n" + "🔮\n" + "🪄\n" + "🧿\n" + "🎮\n" + "🕹️\n" + "🎰\n" + "🎲\n" +
                "🧩\n" + "🧸\n" + "🪅\n" + "🪆\n" + "♠️\n" + "♥️\n" + "♦️\n" + "♣️\n" +
                "♟️\n" + "🃏\n" + "🀄\n" + "🎴\n" + "🎭\n" + "🖼️\n" + "🎨\n" + "🧵\n" +
                "🧶\n" + "🪡\n" + "🧶\n";
        String[] strings = emoji.split("\n");
        return new Emoji("🎃", Arrays.asList(strings));
    }
}
