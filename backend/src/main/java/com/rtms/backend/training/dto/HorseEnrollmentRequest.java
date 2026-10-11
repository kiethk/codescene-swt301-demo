package com.rtms.backend.training.dto;
import com.rtms.backend.stable.entity.StableStall;

/**
 * Một chiến mã trong lời ghi danh theo nhóm.
 * (One horse in a group enrollment.)
 */
public class HorseEnrollmentRequest {

    private Long horseId;

    /**
     * Groom phụ trách. Để null thì hệ thống tự suy từ chuồng của con ngựa
     * (StableStall.groomId) — đúng thực tế: người chăm con ngựa cũng là
     * người dắt nó ra sân.
     * Chỉ điền tay khi cần ghi đè (Groom chính nghỉ ốm chẳng hạn).
     * (The groom in charge. When null the system infers it from the horse stall (StableStall.groomId): the person who looks after the
     *  horse also leads it out. Fill it in manually only to override (for example when the main groom is sick).)
     */
    private Long groomId;

    public Long getHorseId() { return horseId; }
    public void setHorseId(Long horseId) { this.horseId = horseId; }

    public Long getGroomId() { return groomId; }
    public void setGroomId(Long groomId) { this.groomId = groomId; }
}
