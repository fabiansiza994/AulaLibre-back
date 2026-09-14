package com.alulalibre.app.aulalibre.shared;

import static org.assertj.core.api.Assertions.assertThat;

import com.alulalibre.app.aulalibre.block.application.dto.response.BlockResponse;
import com.alulalibre.app.aulalibre.room.application.dto.response.RoomResponse;
import com.alulalibre.app.aulalibre.room.domain.enums.RoomType;
import com.alulalibre.app.aulalibre.roomrequest.domain.enums.RoomRequestStatus;
import com.alulalibre.app.aulalibre.schedule.application.dto.response.ScheduleResponse;
import com.alulalibre.app.aulalibre.user.domain.enums.UserRole;
import java.time.LocalDateTime;
import java.time.LocalTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

/**
 * Locks down the exact JSON shapes BACKEND_API_CONTRACT.md documents:
 * Spanish enum labels, "HH:mm" times with no seconds, and a clean
 * (no-fractional-second) LocalDateTime — the details most likely to silently
 * regress since they depend on Jackson defaults, not just DTO field names.
 */
@SpringBootTest
class JsonContractShapeTest {

    @Autowired
    private JsonMapper jsonMapper;

    @Test
    void roomType_serializesAsSpanishLabel() {
        assertThat(jsonMapper.writeValueAsString(RoomType.CLASSROOM)).isEqualTo("\"Aula\"");
    }

    @Test
    void userRole_serializesAsSpanishLabel() {
        assertThat(jsonMapper.writeValueAsString(UserRole.PROFESSOR)).isEqualTo("\"profesor\"");
    }

    @Test
    void roomRequestStatus_serializesAsSpanishLabel() {
        assertThat(jsonMapper.writeValueAsString(RoomRequestStatus.PENDING)).isEqualTo("\"pendiente\"");
    }

@Test
    void localDateTime_serializesWithoutFractionalSeconds() {
        LocalDateTime timestamp = LocalDateTime.of(2026, 8, 29, 9, 15, 0);
        assertThat(jsonMapper.writeValueAsString(timestamp)).isEqualTo("\"2026-08-29T09:15:00\"");
    }

    @Test
    void roomResponse_isFlatWithBlockIdNoNestedBlock() {
        RoomResponse room = new RoomResponse(10L, "Salón 201", 2L, RoomType.CLASSROOM, 2, 30);
        String json = jsonMapper.writeValueAsString(room);
        assertThat(json).contains("\"blockId\":2").doesNotContain("\"block\"").doesNotContain("\"active\"");
    }

    @Test
    void blockResponse_matchesContractFields() {
        BlockResponse block = new BlockResponse(1L, "B1", "Bloque 1", "desc", true);
        String json = jsonMapper.writeValueAsString(block);
        assertThat(json).contains("\"id\":1", "\"code\":\"B1\"", "\"name\":\"Bloque 1\"", "\"active\":true");
    }

    @Test
    void scheduleResponse_usesStartEndAndSubjectFieldNames() {
        ScheduleResponse schedule = new ScheduleResponse(1L, 10L, "lunes", LocalTime.of(8, 0), LocalTime.of(10, 0), "Programación I");
        String json = jsonMapper.writeValueAsString(schedule);
        assertThat(json).contains("\"start\":\"08:00\"", "\"end\":\"10:00\"", "\"subject\":\"Programación I\"", "\"day\":\"lunes\"");
    }
}
