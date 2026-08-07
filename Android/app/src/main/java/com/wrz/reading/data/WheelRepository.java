package com.wrz.reading.data;

import com.wrz.reading.R;
import com.wrz.reading.app.MyApplication;
import com.wrz.reading.model.Wheel;

import java.util.ArrayList;
import java.util.List;

/**
 * Wheel 转盘数据仓库：统一管理用户转盘与模板的数据库操作。
 * 从 PrefManager 拆分，使 PrefManager 仅负责 SharedPreferences。
 */
public class WheelRepository {

    private static volatile WheelRepository INSTANCE;

    private WheelRepository() {
    }

    public static WheelRepository getInstance() {
        if (INSTANCE == null) {
            synchronized (WheelRepository.class) {
                if (INSTANCE == null) {
                    INSTANCE = new WheelRepository();
                }
            }
        }
        return INSTANCE;
    }

    private WheelDao dao() {
        return MyApplication.wheelDatabase.wheelDao();
    }

    // ==================== 用户转盘 ====================

    /**
     * 读取用户转盘列表
     */
    public List<Wheel> getWheelList() {
        List<Wheel> wheels = dao().getAllUserWheels();
        return wheels != null ? wheels : new ArrayList<>();
    }

    /**
     * 获取上次使用的 Wheel，无则取第一个，再无则返回默认
     */
    public Wheel getLastWheel() {
        WheelDao dao = dao();
        Wheel last = dao.getLastUsedWheel();
        if (last != null) {
            return last;
        }
        List<Wheel> list = dao.getAllUserWheels();
        if (!list.isEmpty()) {
            Wheel first = list.get(0);
            first.setLastTime(true);
            dao.update(first);
            return first;
        }
        return getDefaultWheel();
    }

    /**
     * 获取上次使用的 Wheel 的 Id
     */
    public String getLastWheelId() {
        Wheel last = getLastWheel();
        return last != null ? last.getId() : "";
    }

    /**
     * 获取 Wheel By id，未找到返回 createNewWheel
     */
    public Wheel getWheelById(String id) {
        Wheel wheel = dao().getUserWheelById(id);
        return wheel != null ? wheel : getBlankWheel();
    }

    /**
     * 创建默认的 Wheel（基于 Template 默认选项）
     */
    public Wheel getDefaultWheel() {
        Wheel wheel = new Wheel(
                "-1",
                MyApplication.app.getString(R.string.default_list),
                true,
                MyApplication.template.getDefaultList()
        );
        wheel.setTemplate(false);
        return wheel;
    }

    /**
     * 创建新空白 Wheel
     */
    public Wheel getBlankWheel() {
        Wheel wheel = new Wheel(
                String.valueOf(System.currentTimeMillis()),
                "新列表",
                true,
                new ArrayList<>()
        );
        wheel.setTemplate(false);
        return wheel;
    }

    /**
     * 保存最后一次使用的标记（清除其他标记）
     * 使用事务保证 clearAllLastTime 与 insertOrReplace 的原子性，
     * 避免进程中途被杀导致所有 lastTime 标记丢失。
     */
    public void saveLastTime(Wheel wheel) {
        if (wheel == null) return;
        final WheelDao dao = dao();
        wheel.setLastTime(true);
        wheel.setTemplate(false);
        MyApplication.wheelDatabase.runInTransaction(() -> {
            dao.clearAllLastTime();
            dao.insertOrReplace(wheel);
        });
    }

    /**
     * 添加单个 Wheel 到用户转盘
     */
    public void addWheel(Wheel wheel) {
        if (wheel == null) return;
        wheel.setTemplate(false);
        dao().insertOrReplace(wheel);
    }

    /**
     * 更新 Wheel
     */
    public void updateWheel(Wheel wheel) {
        if (wheel == null) return;
        wheel.setTemplate(false);
        dao().insertOrReplace(wheel);
    }

    /**
     * 删除指定的用户转盘
     */
    public void removeWheel(Wheel wheel) {
        if (wheel != null) {
            dao().delete(wheel);
        }
    }

    /**
     * 获取用户转盘数量
     */
    public int getWheelCount() {
        return dao().getUserWheelCount();
    }

    /**
     * 清空所有用户转盘
     */
    public void clearWheelList() {
        dao().deleteAllUserWheels();
    }

    // ==================== 模板 ====================

    /**
     * 读取模板列表：DB 为空则写入默认模板后返回（懒初始化）
     * 使用 synchronized 防止并发重复写入，事务保证批量插入原子性。
     */
    public synchronized List<Wheel> getTemplateList() {
        final WheelDao dao = dao();
        List<Wheel> templates = dao.getAllTemplates();
        if (templates != null && !templates.isEmpty()) {
            return templates;
        }
        final List<Wheel> defaults = MyApplication.template.getDefaultTemplateList();
        MyApplication.wheelDatabase.runInTransaction(() -> {
            for (Wheel wheel : defaults) {
                wheel.setTemplate(true);
                dao.insertOrReplace(wheel);
            }
        });
        return defaults;
    }

    /**
     * 存储 模板 列表（会覆盖现有的）
     * 使用事务保证删除与插入的原子性，避免中途失败导致模板全部丢失。
     */
    public void saveTemplateList(final List<Wheel> templates) {
        final WheelDao dao = dao();
        MyApplication.wheelDatabase.runInTransaction(() -> {
            dao.deleteAllTemplates();
            if (templates == null) return;
            for (Wheel wheel : templates) {
                wheel.setTemplate(true);
                dao.insert(wheel);
            }
        });
    }

    /**
     * 添加单个模板
     */
    public void addTemplate(Wheel wheel) {
        if (wheel == null) return;
        wheel.setTemplate(true);
        dao().insert(wheel);
    }

    /**
     * 更新模板
     */
    public void updateTemplate(Wheel wheel) {
        if (wheel == null) return;
        wheel.setTemplate(true);
        dao().insertOrReplace(wheel);
    }

    /**
     * 获取模板 By id，未找到返回 null
     */
    public Wheel getTemplateById(String id) {
        if (id == null) return null;
        return dao().getTemplateById(id);
    }

    /**
     * 删除单个模板
     */
    public void delTemplate(Wheel wheel) {
        if (wheel == null) return;
        dao().delete(wheel);
    }
}
