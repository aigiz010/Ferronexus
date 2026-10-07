package com.aigiz010.ferronexus.imprinter;

/** Режимы Импринтера. */
public enum ImprintMode {
    SELECT,         // выделение области или автоматический захват многоблочной структуры
    COPY,           // копирование блоков и настроек
    COPY_CONTENTS,  // копирование вместе с содержимым
    PASTE,          // вставка с предпросмотром
    APPLY_SETTINGS  // применить настройки к уже стоящим блокам
}
