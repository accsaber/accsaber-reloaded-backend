package com.accsaber.backend.util;

import java.util.Map;

public final class HmdMapper {

    private static final Map<Integer, String> ID_TO_NAME = Map.ofEntries(
            Map.entry(0, "Unknown"),
            Map.entry(1, "Rift"),
            Map.entry(2, "Vive"),
            Map.entry(4, "Vive Pro"),
            Map.entry(8, "WMR"),
            Map.entry(16, "Rift S"),
            Map.entry(32, "Quest"),
            Map.entry(33, "Pico Neo 3"),
            Map.entry(34, "Pico Neo 2"),
            Map.entry(35, "Vive Pro 2"),
            Map.entry(36, "Vive Elite"),
            Map.entry(37, "Miramar"),
            Map.entry(38, "Pimax 8K"),
            Map.entry(39, "Pimax 5K"),
            Map.entry(40, "Pimax Artisan"),
            Map.entry(41, "HP Reverb"),
            Map.entry(42, "Samsung WMR"),
            Map.entry(43, "Qiyu Dream"),
            Map.entry(44, "Disco"),
            Map.entry(45, "Lenovo Explorer"),
            Map.entry(46, "Acer WMR"),
            Map.entry(47, "Vive Focus"),
            Map.entry(48, "Arpara"),
            Map.entry(49, "Dell Visor"),
            Map.entry(50, "E3"),
            Map.entry(51, "Vive DVT"),
            Map.entry(52, "Glasses 20"),
            Map.entry(53, "Hedy"),
            Map.entry(54, "Vaporeon"),
            Map.entry(55, "Huawei VR"),
            Map.entry(56, "Asus WMR"),
            Map.entry(57, "Cloud XR"),
            Map.entry(58, "VRidge"),
            Map.entry(59, "Medion"),
            Map.entry(60, "Pico Neo 4"),
            Map.entry(61, "Quest Pro"),
            Map.entry(62, "Pimax Crystal"),
            Map.entry(63, "E4"),
            Map.entry(64, "Index"),
            Map.entry(65, "Controllable"),
            Map.entry(66, "Bigscreen Beyond"),
            Map.entry(67, "Nolo Sonic"),
            Map.entry(68, "Hypereal"),
            Map.entry(69, "Varjo Aero"),
            Map.entry(70, "PSVR 2"),
            Map.entry(71, "Megane 1"),
            Map.entry(72, "Varjo XR-3"),
            Map.entry(73, "MeganeX Superlight"),
            Map.entry(74, "Somnium VR1"),
            Map.entry(75, "Steam Frame"),
            Map.entry(128, "Vive Cosmos"),
            Map.entry(256, "Quest 2"),
            Map.entry(512, "Quest 3"),
            Map.entry(513, "Quest 3S"));

    private HmdMapper() {
    }

    public static String fromBeatLeaderId(Integer id) {
        if (id == null || id == 0) {
            return null;
        }
        return ID_TO_NAME.getOrDefault(id, "Unknown");
    }

    public static String normalize(String hmd) {
        if (hmd == null || hmd.isBlank()) {
            return null;
        }
        try {
            int id = Integer.parseInt(hmd);
            return fromBeatLeaderId(id);
        } catch (NumberFormatException e) {
            return hmd;
        }
    }
}
