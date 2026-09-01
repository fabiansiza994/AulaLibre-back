package com.alulalibre.app.aulalibre.block.application.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.alulalibre.app.aulalibre.block.application.dto.response.BlockResponse;
import com.alulalibre.app.aulalibre.block.application.dto.response.BlockSummaryResponse;
import com.alulalibre.app.aulalibre.block.domain.model.Block;
import org.junit.jupiter.api.Test;

class BlockMapperTest {

    private final BlockMapper mapper = new BlockMapper();

    @Test
    void toResponse_copiesAllFields() {
        Block block = new Block();
        block.setId(1L);
        block.setCode("B1");
        block.setName("Bloque 1");
        block.setDescription("Edificio principal");
        block.setActive(true);

        BlockResponse response = mapper.toResponse(block);

        assertThat(response).isEqualTo(new BlockResponse(1L, "B1", "Bloque 1", "Edificio principal", true));
    }

    @Test
    void toSummary_onlyKeepsIdCodeAndName() {
        Block block = new Block();
        block.setId(2L);
        block.setCode("B2");
        block.setName("Bloque 2");

        BlockSummaryResponse summary = mapper.toSummary(block);

        assertThat(summary).isEqualTo(new BlockSummaryResponse(2L, "B2", "Bloque 2"));
    }
}
