package com.rtms.backend.training.service;

import com.rtms.backend.config.FarmSchedulePolicy;
import com.rtms.backend.config.TrainingDaySetConverter;
import com.rtms.backend.training.entity.TrainingLot;
import com.rtms.backend.training.enums.TrainingDay;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TrainingLotServiceTest {

    private final TrainingLotService lotService = new TrainingLotService(null, null, null);

    @Test
    @DisplayName("findFirstFreeSlot: with no lot yet the first slot is 06:00")
    void testFindFirstFreeSlot_EmptyDay() {
        List<TrainingLot> lots = List.of();
        LocalTime slot = lotService.findFirstFreeSlot(lots, 60);

        assertNotNull(slot);
        assertEquals(LocalTime.of(6, 0), slot);
    }

    @Test
    @DisplayName("findFirstFreeSlot: with a lot at 06:00-07:30 the next 60-minute subject starts at 07:30")
    void testFindFirstFreeSlot_Sequential() {
        TrainingLot lot1 = new TrainingLot();
        lot1.setStartTime(LocalTime.of(6, 0));
        lot1.setEndTime(LocalTime.of(7, 30));

        LocalTime slot = lotService.findFirstFreeSlot(List.of(lot1), 60);

        assertNotNull(slot);
        assertEquals(LocalTime.of(7, 30), slot);
    }

    @Test
    @DisplayName("findFirstFreeSlot: finds a gap between lots (first-fit gap)")
    void testFindFirstFreeSlot_GapInMiddle() {
        TrainingLot lot1 = new TrainingLot();
        lot1.setStartTime(LocalTime.of(6, 0));
        lot1.setEndTime(LocalTime.of(7, 0));

        TrainingLot lot2 = new TrainingLot();
        lot2.setStartTime(LocalTime.of(8, 0));
        lot2.setEndTime(LocalTime.of(9, 30));

        // Khe ở giữa 07:00-08:00 có 60 phút. Bài 60 phút nhét vừa khít lúc 07:00 (The 07:00-08:00 gap is 60 minutes, so a 60-minute subject fits exactly at 07:00)
        LocalTime slot = lotService.findFirstFreeSlot(List.of(lot1, lot2), 60);

        assertNotNull(slot);
        assertEquals(LocalTime.of(7, 0), slot);
    }

    @Test
    @DisplayName("findFirstFreeSlot: when the middle gap is too small it moves to the tail slot")
    void testFindFirstFreeSlot_GapTooSmall_TakesTail() {
        TrainingLot lot1 = new TrainingLot();
        lot1.setStartTime(LocalTime.of(6, 0));
        lot1.setEndTime(LocalTime.of(7, 0));

        TrainingLot lot2 = new TrainingLot();
        lot2.setStartTime(LocalTime.of(7, 30)); // Khe giữa chỉ 30 phút (07:00 - 07:30) (the middle gap is only 30 minutes)
        lot2.setEndTime(LocalTime.of(8, 30));

        // Bài 60 phút không vừa khe giữa (30'), phải sang sau lot2 (08:30) (a 60-minute subject does not fit the 30' gap, so it goes after lot2 (08:30))
        LocalTime slot = lotService.findFirstFreeSlot(List.of(lot1, lot2), 60);

        assertNotNull(slot);
        assertEquals(LocalTime.of(8, 30), slot);
    }

    @Test
    @DisplayName("findFirstFreeSlot: no time left in the 06:00-10:00 golden window -> returns null")
    void testFindFirstFreeSlot_FullDay_ReturnsNull() {
        TrainingLot lot1 = new TrainingLot();
        lot1.setStartTime(LocalTime.of(6, 0));
        lot1.setEndTime(LocalTime.of(7, 30)); // 90'

        TrainingLot lot2 = new TrainingLot();
        lot2.setStartTime(LocalTime.of(7, 30));
        lot2.setEndTime(LocalTime.of(8, 30)); // 60'

        TrainingLot lot3 = new TrainingLot();
        lot3.setStartTime(LocalTime.of(8, 30));
        lot3.setEndTime(LocalTime.of(9, 30)); // 60' (tổng 210', còn 30' đuôi 09:30-10:00) (60', 210' in total, 30' left in the tail 09:30-10:00)

        // Bài 45 phút không nhét vừa 30 phút còn lại -> trả về null (a 45-minute subject does not fit the 30 minutes left -> null)
        LocalTime slot = lotService.findFirstFreeSlot(List.of(lot1, lot2, lot3), 45);

        assertNull(slot);
    }

    @Test
    @DisplayName("calculateEndDate: exact end date for a Mon/Wed/Fri schedule")
    void testCalculateEndDate() {
        // Bắt đầu Thứ Hai 2026-10-05, tập T2, T4, T6, tổng 6 buổi (starts Monday 2026-10-05, trains Mon/Wed/Fri, 6 sessions in total)
        LocalDate start = LocalDate.of(2026, 10, 5); // Monday
        Set<TrainingDay> days = Set.of(TrainingDay.MONDAY, TrainingDay.WEDNESDAY, TrainingDay.FRIDAY);

        // Buổi 1: T2 05/10, Buổi 2: T4 07/10, Buổi 3: T6 09/10 (Sessions 1-3: Mon 05/10, Wed 07/10, Fri 09/10)
        // Buổi 4: T2 12/10, Buổi 5: T4 14/10, Buổi 6: T6 16/10 (Sessions 4-6: Mon 12/10, Wed 14/10, Fri 16/10)
        LocalDate end = HorseTrainingPlanService.calculateEndDate(start, days, 6);

        assertEquals(LocalDate.of(2026, 10, 16), end);
    }

    @Test
    @DisplayName("TrainingDaySetConverter: converts Set<TrainingDay> to a sorted string and back")
    void testTrainingDaySetConverter() {
        TrainingDaySetConverter converter = new TrainingDaySetConverter();

        Set<TrainingDay> input = Set.of(TrainingDay.FRIDAY, TrainingDay.MONDAY, TrainingDay.WEDNESDAY);
        String dbData = converter.convertToDatabaseColumn(input);

        // Đã sorted theo Enum name (sorted by enum name)
        assertEquals("FRIDAY,MONDAY,WEDNESDAY", dbData);

        Set<TrainingDay> entityAttr = converter.convertToEntityAttribute(dbData);
        assertEquals(3, entityAttr.size());
        assertTrue(entityAttr.contains(TrainingDay.MONDAY));
        assertTrue(entityAttr.contains(TrainingDay.WEDNESDAY));
        assertTrue(entityAttr.contains(TrainingDay.FRIDAY));
    }
}
