package com.alulalibre.app.aulalibre.block.application.service;

import com.alulalibre.app.aulalibre.block.application.dto.request.CreateBlockRequest;
import com.alulalibre.app.aulalibre.block.application.dto.request.UpdateBlockRequest;
import com.alulalibre.app.aulalibre.block.application.dto.response.BlockResponse;
import com.alulalibre.app.aulalibre.block.application.mapper.BlockMapper;
import com.alulalibre.app.aulalibre.block.domain.model.Block;
import com.alulalibre.app.aulalibre.block.domain.repository.BlockRepository;
import com.alulalibre.app.aulalibre.room.domain.repository.RoomRepository;
import com.alulalibre.app.aulalibre.shared.exception.ConflictException;
import com.alulalibre.app.aulalibre.shared.exception.ErrorCode;
import com.alulalibre.app.aulalibre.shared.exception.ResourceNotFoundException;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockService {

    private final BlockRepository blockRepository;
    private final BlockMapper blockMapper;
    private final RoomRepository roomRepository;

    public BlockService(BlockRepository blockRepository, BlockMapper blockMapper, RoomRepository roomRepository) {
        this.blockRepository = blockRepository;
        this.blockMapper = blockMapper;
        this.roomRepository = roomRepository;
    }

    @Transactional
    public BlockResponse create(CreateBlockRequest request) {
        String code = request.code().toUpperCase();
        if (blockRepository.existsByCodeIgnoreCase(code)) {
            throw new ConflictException(ErrorCode.DUPLICATE_BLOCK_CODE,
                    "Ya existe un bloque con el código '%s'".formatted(code));
        }
        Block block = new Block();
        block.setCode(code);
        block.setName(request.name());
        block.setDescription(request.description());
        return blockMapper.toResponse(blockRepository.save(block));
    }

    @Transactional
    public BlockResponse update(Long id, UpdateBlockRequest request) {
        Block block = findEntityById(id);
        String code = request.code().toUpperCase();
        blockRepository.findByCodeIgnoreCase(code)
                .filter(existing -> !existing.getId().equals(id))
                .ifPresent(existing -> {
                    throw new ConflictException(ErrorCode.DUPLICATE_BLOCK_CODE,
                            "Ya existe un bloque con el código '%s'".formatted(code));
                });
        block.setCode(code);
        block.setName(request.name());
        block.setDescription(request.description());
        return blockMapper.toResponse(block);
    }

    @Transactional
    public BlockResponse toggleActive(Long id) {
        Block block = findEntityById(id);
        block.setActive(!block.isActive());
        return blockMapper.toResponse(block);
    }

    @Transactional
    public void delete(Long id) {
        Block block = findEntityById(id);
        if (roomRepository.countByBlockId(id) > 0) {
            throw new ConflictException(ErrorCode.BLOCK_HAS_ROOMS,
                    "No se puede eliminar %s: todavía tiene salones asignados.".formatted(block.getCode()));
        }
        blockRepository.delete(block);
    }

    @Transactional(readOnly = true)
    public BlockResponse findById(Long id) {
        return blockMapper.toResponse(findEntityById(id));
    }

    @Transactional(readOnly = true)
    public List<BlockResponse> findAll() {
        return blockRepository.findAll().stream().map(blockMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Block findEntityById(Long id) {
        return blockRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.BLOCK_NOT_FOUND,
                        "El bloque solicitado no existe"));
    }
}
