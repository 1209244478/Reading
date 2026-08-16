package com.wrz.reading.ui.wheel.data;

import androidx.core.content.ContextCompat;

import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.ui.wheel.model.Option;
import com.wrz.reading.ui.wheel.model.Wheel;

import java.util.ArrayList;
import java.util.List;

public class Template {

    public Template() {
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_red));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_blue));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_light_blue));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_green));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_light_pink));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_yellow));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_purple));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_mint));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_gold));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_lavender));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_sky));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_emerald));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_dark_blue));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_light_red));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_banana));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_light_green));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_light_yellow));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_light_purple));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_light_orange));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_light_pink));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_light_brown));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_eggshell));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_forest));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_violet));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_aqua));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_dark_orange));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_sand));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_dark_purple));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_cotton));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_rose));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_cerulean));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_tangerine));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_tomato));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_azure));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_alabaster));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_indigo));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_dark_pink));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_orange));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_gray));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_cyan));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_lemon));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_jade));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_light_gray));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_ivory));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_dark_green));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_dark_red));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_beige));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_teal));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_peach));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_navy));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_pink));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_lime));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_lilac));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_coral));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_magenta));
        colors.add(ContextCompat.getColor(MyApplication.app, R.color.apple_cream));
    }

    private final List<Integer> colors = new ArrayList<>();

    public List<Integer> getColors() {
        return colors;
    }

    // 热门旅游城市
    public List<Option> getHotCityList() {
        List<Option> hotCityList = new ArrayList<>();
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_chongqing)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_beijing)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_shanghai)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_guangzhou)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_shenzhen)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_hangzhou)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_chengdu)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_xian)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_nanjing)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_suzhou)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_xiamen)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_qingdao)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_sanya)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_lijiang)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_guilin)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_zhangjiajie)));
        hotCityList.add(new Option(MyApplication.app.getString(R.string.city_lhasa)));

        for (int i = 0; i < hotCityList.size(); i++) {
            hotCityList.get(i).setColor(colors.get(i));
        }
        return hotCityList;
    }


    // 默认Option列表
    public List<Option> getDefaultList() {
        List<Option> defaultList = new ArrayList<>();
        // 使用资源字符串代替硬编码
        defaultList.add(new Option(MyApplication.app.getString(R.string.food_hotpot_chain)));
        defaultList.add(new Option(MyApplication.app.getString(R.string.food_hotpot)));
        defaultList.add(new Option(MyApplication.app.getString(R.string.food_bbq)));
        defaultList.add(new Option(MyApplication.app.getString(R.string.food_pizza)));
        defaultList.add(new Option(MyApplication.app.getString(R.string.food_hamburger)));
        defaultList.add(new Option(MyApplication.app.getString(R.string.food_sushi)));
        defaultList.add(new Option(MyApplication.app.getString(R.string.food_spicy_pot)));
        defaultList.add(new Option(MyApplication.app.getString(R.string.food_sichuan_cuisine)));
        defaultList.add(new Option(MyApplication.app.getString(R.string.food_cantonese_cuisine)));
        defaultList.add(new Option(MyApplication.app.getString(R.string.food_korean_food)));

        for (int i = 0; i < defaultList.size(); i++) {
            defaultList.get(i).setColor(colors.get(i));
        }
        return defaultList;
    }

    public List<Option> getLoveMyselfList() {
        List<Option> loveMyself = new ArrayList<>();
        // 使用资源字符串代替硬编码
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_meditation)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_exercise)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_reading)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_diary)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_boundaries)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_healthy_food)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_hobby)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_friends)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_reflection)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_break)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_learn)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_sleep)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_volunteer)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_nature)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_gratitude)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_acceptance)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_gift)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_breathing)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_positive)));
        loveMyself.add(new Option(MyApplication.app.getString(R.string.self_love_health_check)));

        for (int i = 0; i < loveMyself.size(); i++) {
            loveMyself.get(i).setColor(colors.get(i));
        }
        return loveMyself;
    }

    /**
     * 早餐
     */
    public List<Option> getWhatToEatBreakfast() {
        List<Option> foodList = new ArrayList<>();

        // 早餐选项
        // 使用资源字符串代替硬编码
        foodList.add(new Option(MyApplication.app.getString(R.string.food_soy_milk_youtiao)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_egg_sandwich)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_oatmeal_milk)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_pidan_porridge)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_green_vegetable_porridge)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_sweet_potato_porridge)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_oatmeal_milk_porridge)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_mung_bean_porridge)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_corn_pumpkin_porridge)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_red_bean_porridge)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_fuding_pork_slice)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_eight_treasure_porridge)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_laba_porridge)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_rice_ball)));


        for (int i = 0; i < foodList.size(); i++) {
            foodList.get(i).setColor(colors.get(i));
        }
        return foodList;
    }

    /**
     * 午餐
     */
    public List<Option> getWhatToEatLunch() {
        List<Option> foodList = new ArrayList<>();
        // 午餐选项
        // 使用资源字符串代替硬编码
        foodList.add(new Option(MyApplication.app.getString(R.string.food_rice_tomato_egg)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_beef_noodles)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_spicy_pot)));  // 复用之前的字符串
        foodList.add(new Option(MyApplication.app.getString(R.string.food_covered_rice)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_sandwich_salad)));


        for (int i = 0; i < foodList.size(); i++) {
            foodList.get(i).setColor(colors.get(i));
        }
        return foodList;
    }

    /**
     * 晚餐
     */
    public List<Option> getWhatToEatDinner() {
        List<Option> foodList = new ArrayList<>();
        // 晚餐选项
        // 使用资源字符串代替硬编码
        foodList.add(new Option(MyApplication.app.getString(R.string.food_braised_pork_with_vegetables)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_steamed_fish_with_rice)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_dumplings)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_hotpot)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_beef_steak_with_vegetables)));


        for (int i = 0; i < foodList.size(); i++) {
            foodList.get(i).setColor(colors.get(i));
        }
        return foodList;
    }

    /**
     * 广东早茶
     */
    public List<Option> getCantoneseMorningTea() {
        List<Option> foodList = new ArrayList<>();
        // 选项
        // 使用资源字符串代替硬编码
        foodList.add(new Option(MyApplication.app.getString(R.string.food_shrimp_dumpling)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_dry_shumai)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_char_siu_bun)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_egg_tart)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_chicken_feet)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_pork_ribs)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_glutinous_rice_chicken)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_rice_noodle_roll)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_bean_curd_roll)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_spring_roll)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_pan_fried_dumplings)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_salty_corn_cake)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_radish_cake)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_taro_cake)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_salty_egg_yolk_bun)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_custard_bun)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_mala_cake)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_coconut_jelly)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_mango_pomelo_sago)));


        for (int i = 0; i < foodList.size(); i++) {
            foodList.get(i).setColor(colors.get(i));
        }
        return foodList;
    }

    /**
     * 京爷早餐
     */
    public List<Option> getBeijingMorningFood() {
        List<Option> foodList = new ArrayList<>();
        // 选项
        // 使用资源字符串代替硬编码
        foodList.add(new Option(MyApplication.app.getString(R.string.food_douzhi)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_chaogan)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_miancha)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_doufunao)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_millet_porridge)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_shaobing)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_youbing)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_baozi)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_xianbing)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_dalianhuoshao)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_zhagao)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_qiegao)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_jiaoquan)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_xiancaisi)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_luzhu_huoshao)));


        for (int i = 0; i < foodList.size(); i++) {
            foodList.get(i).setColor(colors.get(i));
        }
        return foodList;
    }

    /**
     * 上海早餐
     */
    public List<Option> getShanghaiMorningFood() {
        List<Option> foodList = new ArrayList<>();
        // 选项
        // 使用资源字符串代替硬编码
        foodList.add(new Option(MyApplication.app.getString(R.string.food_shengjian)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_xiaolongbao)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_dabing)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_youtiao)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_shaobing)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_mantou)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_huajuan)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_doujiang)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_paofan)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_xiaohuntun)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_yangchunmian)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_xianjiang)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_congyoubing)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_laohujiaozhao)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_cifantuan)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_danbing)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_guotie)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_tanggao)));


        for (int i = 0; i < foodList.size(); i++) {
            foodList.get(i).setColor(colors.get(i));
        }
        return foodList;
    }

    /**
     * 杭州早餐
     */
    public List<Option> getHangZhouMorningFood() {
        List<Option> foodList = new ArrayList<>();
        // 选项
        // 使用资源字符串代替硬编码
        foodList.add(new Option(MyApplication.app.getString(R.string.food_west_lake_vinegar_fish)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_kfc)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_mcdonalds)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_tastien)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_wallace)));
        foodList.add(new Option(MyApplication.app.getString(R.string.food_catch_fish_west_lake)));


        for (int i = 0; i < foodList.size(); i++) {
            foodList.get(i).setColor(colors.get(i));
        }
        return foodList;
    }

    /**
     * 约会做什么
     */
    public List<Option> getDating() {
        List<Option> loveList = new ArrayList<>();
        // 选项
        // 使用资源字符串代替硬编码
        loveList.add(new Option(MyApplication.app.getString(R.string.activity_watch_movie)));
        loveList.add(new Option(MyApplication.app.getString(R.string.activity_eat_dinner)));
        loveList.add(new Option(MyApplication.app.getString(R.string.activity_visit_art_museum)));
        loveList.add(new Option(MyApplication.app.getString(R.string.activity_visit_museum)));
        loveList.add(new Option(MyApplication.app.getString(R.string.activity_walk_in_park)));
        loveList.add(new Option(MyApplication.app.getString(R.string.activity_local_travel)));
        loveList.add(new Option(MyApplication.app.getString(R.string.activity_watch_concert)));
        loveList.add(new Option(MyApplication.app.getString(R.string.activity_werewolf_game)));
        loveList.add(new Option(MyApplication.app.getString(R.string.activity_hike)));
        loveList.add(new Option(MyApplication.app.getString(R.string.activity_shopping)));


        for (int i = 0; i < loveList.size(); i++) {
            loveList.get(i).setColor(colors.get(i));
        }
        return loveList;
    }

    /**
     * 送女朋友什么礼物
     */
    public List<Option> getGiftToGirl() {
        List<Option> giftList = new ArrayList<>();
        // 选项
        // 使用资源字符串代替硬编码
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_bag)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_flowers)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_clothes)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_lip_gloss)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_necklace)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_ring)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_lipstick)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_foundation)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_highlighter)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_brightening)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_earrings)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_eyebrow_pencil)));

        /*giftList.add(new Option("三年高考五年模拟"));*/

        for (int i = 0; i < giftList.size(); i++) {
            giftList.get(i).setColor(colors.get(i));
        }
        return giftList;
    }


    /**
     * 送男朋友什么礼物
     */
    public List<Option> getGiftToBoy() {
        List<Option> giftList = new ArrayList<>();
        // 选项
        // 使用资源字符串代替硬编码
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_sneakers)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_switch)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_ps5)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_keyboard)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_gpu)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_headphones)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_camera)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_watch)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_carrier_battle_group)));
        giftList.add(new Option(MyApplication.app.getString(R.string.gift_gaokao_books)));


        for (int i = 0; i < giftList.size(); i++) {
            giftList.get(i).setColor(colors.get(i));
        }
        return giftList;
    }



    /**
     * 国内旅游城市
     */
    public List<Option> getTravelCity() {
        List<Option> cityList = new ArrayList<>();
        // 选项
        // 使用资源字符串代替硬编码
        cityList.add(new Option(MyApplication.app.getString(R.string.city_beijing)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_shanghai)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_qingdao)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_xian)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_hangzhou)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_suzhou)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_lijiang)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_xinjiang)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_xiamen)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_sanya)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_macao)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_taipei)));


        for (int i = 0; i < cityList.size(); i++) {
            cityList.get(i).setColor(colors.get(i));
        }
        return cityList;
    }


    /**
     * 亚洲旅游城市
     */
    public List<Option> getAsiaTravelCity() {
        List<Option> cityList = new ArrayList<>();
        // 选项
        // 使用资源字符串代替硬编码
        cityList.add(new Option(MyApplication.app.getString(R.string.city_tokyo)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_seoul)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_bangkok)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_bali)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_singapore)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_phuket)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_jakarta)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_chiang_mai)));


        for (int i = 0; i < cityList.size(); i++) {
            cityList.get(i).setColor(colors.get(i));
        }
        return cityList;
    }


    /**
     * 亚洲旅游城市
     */
    public List<Option> getWorldTravelCity() {
        List<Option> cityList = new ArrayList<>();
        // 选项
        // 使用资源字符串代替硬编码
        cityList.add(new Option(MyApplication.app.getString(R.string.city_paris)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_dubai)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_madrid)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_amsterdam)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_berlin)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_rome)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_new_york)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_barcelona)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_london)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_munich)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_milan)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_dublin)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_vienna)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_los_angeles)));
        cityList.add(new Option(MyApplication.app.getString(R.string.city_prague)));


        for (int i = 0; i < cityList.size(); i++) {
            cityList.get(i).setColor(colors.get(i));
        }
        return cityList;
    }



    /**
     * 真心话 & 大冒险
     */
    public List<Option> getTrueWordOrAdventure() {
        List<Option> list = new ArrayList<>();
        // 选项
        list.add(new Option(MyApplication.app.getString(R.string.truth)));
        list.add(new Option(MyApplication.app.getString(R.string.dare)));
        /*list.add(new Option("反真心话"));
        list.add(new Option("反大冒险"));*/

        for (int i = 0; i < list.size(); i++) {
            list.get(i).setColor(colors.get(i));
        }
        return list;
    }

    /**
     * 真心话
     */
    public List<Option> getTrueWord() {
        List<Option> list = new ArrayList<>();
        // 选项
        // 使用资源字符串代替硬编码
        list.add(new Option(MyApplication.app.getString(R.string.question_dinner_choice)));
        list.add(new Option(MyApplication.app.getString(R.string.question_cry_moment)));
        list.add(new Option(MyApplication.app.getString(R.string.question_animal)));
        list.add(new Option(MyApplication.app.getString(R.string.question_secret_photos)));
        list.add(new Option(MyApplication.app.getString(R.string.question_embarrassing_date)));
        list.add(new Option(MyApplication.app.getString(R.string.question_opposite_gender)));
        list.add(new Option(MyApplication.app.getString(R.string.question_deep_secret)));
        list.add(new Option(MyApplication.app.getString(R.string.question_hug_choice)));
        list.add(new Option(MyApplication.app.getString(R.string.question_favorite_body_part)));


        for (int i = 0; i < list.size(); i++) {
            list.get(i).setColor(colors.get(i));
        }
        return list;
    }

    /**
     * 大冒险
     */
    public List<Option> getAdventure() {
        List<Option> list = new ArrayList<>();
        // 选项
        // 使用资源字符串代替硬编码
        list.add(new Option(MyApplication.app.getString(R.string.task_princess_carry_opposite)));
        list.add(new Option(MyApplication.app.getString(R.string.task_princess_carry_same)));
        list.add(new Option(MyApplication.app.getString(R.string.task_sing)));
        list.add(new Option(MyApplication.app.getString(R.string.task_sexy_expression)));
        list.add(new Option(MyApplication.app.getString(R.string.task_hold_hands_stare)));
        list.add(new Option(MyApplication.app.getString(R.string.task_post_funny_moment)));
        list.add(new Option(MyApplication.app.getString(R.string.task_imitate_leader)));
        list.add(new Option(MyApplication.app.getString(R.string.task_joke_dialect)));
        list.add(new Option(MyApplication.app.getString(R.string.task_pushups)));


        for (int i = 0; i < list.size(); i++) {
            list.get(i).setColor(colors.get(i));
        }
        return list;
    }



    /**
     * 运动
     */
    public List<Option> getSport() {
        List<Option> list = new ArrayList<>();
        // 选项
        // 使用资源字符串代替硬编码
        list.add(new Option(MyApplication.app.getString(R.string.sport_running)));
        list.add(new Option(MyApplication.app.getString(R.string.sport_swimming)));
        list.add(new Option(MyApplication.app.getString(R.string.sport_yoga)));
        list.add(new Option(MyApplication.app.getString(R.string.sport_dancing)));
        list.add(new Option(MyApplication.app.getString(R.string.sport_fitness)));
        list.add(new Option(MyApplication.app.getString(R.string.sport_cycling)));
        list.add(new Option(MyApplication.app.getString(R.string.sport_hiking)));
        list.add(new Option(MyApplication.app.getString(R.string.sport_mountain_climbing)));
        list.add(new Option(MyApplication.app.getString(R.string.sport_badminton)));
        list.add(new Option(MyApplication.app.getString(R.string.sport_basketball)));
        list.add(new Option(MyApplication.app.getString(R.string.sport_football)));


        for (int i = 0; i < list.size(); i++) {
            list.get(i).setColor(colors.get(i));
        }
        return list;
    }

    /**
     * 健身器材
     */
    public List<Option> getSportEquipment() {
        List<Option> list = new ArrayList<>();
        // 选项
        // 使用资源字符串代替硬编码
        list.add(new Option(MyApplication.app.getString(R.string.equipment_treadmill)));
        list.add(new Option(MyApplication.app.getString(R.string.equipment_spinning_bike)));
        list.add(new Option(MyApplication.app.getString(R.string.equipment_dumbbell)));
        list.add(new Option(MyApplication.app.getString(R.string.equipment_squat_rack)));
        list.add(new Option(MyApplication.app.getString(R.string.equipment_push_up_board)));
        list.add(new Option(MyApplication.app.getString(R.string.equipment_rowing_machine)));
        list.add(new Option(MyApplication.app.getString(R.string.equipment_arm_strengthening_rod)));
        list.add(new Option(MyApplication.app.getString(R.string.equipment_stair_climber)));
        list.add(new Option(MyApplication.app.getString(R.string.equipment_stepper)));
        list.add(new Option(MyApplication.app.getString(R.string.equipment_walking_machine)));


        for (int i = 0; i < list.size(); i++) {
            list.get(i).setColor(colors.get(i));
        }
        return list;
    }

    // ==================== 默认模板列表 ====================

    /**
     * 获取默认转盘模板列表，每个模板使用稳定的硬编码 ID
     */
    public List<Wheel> getDefaultTemplateList() {
        List<Wheel> list = new ArrayList<>();

        // 热门
        list.add(createDefaultTemplate("default_hot_cities", R.string.hot, "\u2708\uFE0F", R.string.hot_cities, 0, getHotCityList()));
        list.add(createDefaultTemplate("default_self_care", R.string.hot, "\uD83E\uDE77", R.string.self_care_ways, 0, getLoveMyselfList()));
        list.add(createDefaultTemplate("default_today_food", R.string.hot, "\uD83C\uDF72", R.string.today_food, 0, getDefaultList()));

        // 美食
        list.add(createDefaultTemplate("default_breakfast", R.string.food, "\uD83C\uDF73", R.string.breakfast, 0, getWhatToEatBreakfast()));
        list.add(createDefaultTemplate("default_lunch", R.string.food, "\uD83C\uDF5A", R.string.lunch, 0, getWhatToEatLunch()));
        list.add(createDefaultTemplate("default_dinner", R.string.food, "\uD83C\uDF5C", R.string.dinner, 0, getWhatToEatDinner()));

        // 各地美食
        list.add(createDefaultTemplate("default_cantonese", R.string.regional_cuisine, "\uD83E\uDD43", R.string.cantonese_cuisine, 0, getCantoneseMorningTea()));
        list.add(createDefaultTemplate("default_beijing", R.string.regional_cuisine, "\uD83E\uDD5B", R.string.beijing_cuisine, 0, getBeijingMorningFood()));
        list.add(createDefaultTemplate("default_shanghai", R.string.regional_cuisine, "\uD83C\uDF77", R.string.shanghai_cuisine, 0, getShanghaiMorningFood()));
        list.add(createDefaultTemplate("default_hangzhou", R.string.regional_cuisine, "\uD83E\uDDCB", R.string.hangzhou_cuisine, 0, getHangZhouMorningFood()));

        // 恋爱
        list.add(createDefaultTemplate("default_date_ideas", R.string.love_title, "\u2764", R.string.date_ideas, 0, getDating()));
        list.add(createDefaultTemplate("default_gift_girlfriend", R.string.love_title, "\uD83D\uDC8C", R.string.gift_for_girlfriend, 0, getGiftToGirl()));
        list.add(createDefaultTemplate("default_gift_boyfriend", R.string.love_title, "\uD83D\uDC8C", R.string.gift_for_boyfriend, 0, getGiftToBoy()));

        // 旅游
        list.add(createDefaultTemplate("default_travel_china", R.string.travel_title, "\uD83C\uDFC4", R.string.holiday_destinations, R.string.china_version, getTravelCity()));
        list.add(createDefaultTemplate("default_travel_asia", R.string.travel_title, "\u2708\uFE0F", R.string.holiday_destinations, R.string.asia_version, getAsiaTravelCity()));
        list.add(createDefaultTemplate("default_travel_global", R.string.travel_title, "\u2708\uFE0F", R.string.holiday_destinations, R.string.global_version, getWorldTravelCity()));

        // 游戏
        list.add(createDefaultTemplate("default_truth_or_dare", R.string.game, "\uD83C\uDF7B", R.string.truth_or_dare, 0, getTrueWordOrAdventure()));
        list.add(createDefaultTemplate("default_truth", R.string.game, "\uD83C\uDF7A", R.string.truth, 0, getTrueWord()));
        list.add(createDefaultTemplate("default_dare", R.string.game, "\uD83E\uDED7", R.string.dare, 0, getAdventure()));

        // 运动
        list.add(createDefaultTemplate("default_workout", R.string.sports_title, "\uD83C\uDFC3", R.string.workout_today, 0, getSport()));
        list.add(createDefaultTemplate("default_fitness", R.string.sports_title, "\uD83C\uDFCB\uFE0F", R.string.fitness_equipment, 0, getSportEquipment()));

        return list;
    }

    /**
     * 构建单个默认模板 Wheel（含 category），使用稳定的硬编码 ID
     */
    private Wheel createDefaultTemplate(String id, int categoryRes, String emoji, int titleRes, int subtitleRes, List<Option> options) {
        String category = MyApplication.app.getString(categoryRes);
        String title = MyApplication.app.getString(titleRes);
        String subtitle = subtitleRes != 0 ? MyApplication.app.getString(subtitleRes) : "";
        Wheel wheel = new Wheel(id, emoji, title, subtitle, options);
        wheel.setCategory(category);
        return wheel;
    }

}
