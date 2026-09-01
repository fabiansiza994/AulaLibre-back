package com.alulalibre.app.aulalibre.block.application.mapper;

import com.alulalibre.app.aulalibre.block.application.dto.response.BlockResponse;
import com.alulalibre.app.aulalibre.block.application.dto.response.BlockSummaryResponse;
import com.alulalibre.app.aulalibre.block.domain.model.Block;
import org.springframework.stereotype.Component;

@Component
public class BlockMapper {

    public BlockResponse toResponse(Block block) {
        return new BlockResponse(
                block.getId(),
                block.getCode(),
                block.getName(),
                block.getDescription(),
                block.isActive());
    }

    public BlockSummaryResponse toSummary(Block block) {
        return new BlockSummaryResponse(block.getId(), block.getCode(), block.getName());
    }
}
