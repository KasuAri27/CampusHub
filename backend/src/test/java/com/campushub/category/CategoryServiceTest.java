package com.campushub.category;

import com.campushub.common.BusinessException;
import com.campushub.task.TaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock CategoryRepository categoryRepository;
    @Mock TaskRepository taskRepository;
    @InjectMocks CategoryService service;

    @Test
    void createAppliesDefaultsForOptionalFields() {
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category created = service.create("代购", null, null, null);

        assertThat(created.getName()).isEqualTo("代购");
        assertThat(created.getHasReview()).isFalse();
        assertThat(created.getSortOrder()).isZero();
    }

    @Test
    void updateChangesOnlyProvidedFields() {
        Category existing = Category.builder().id(3).name("旧名").icon("old.png").hasReview(true).sortOrder(5).build();
        when(categoryRepository.findById(3)).thenReturn(Optional.of(existing));
        when(categoryRepository.save(any(Category.class))).thenAnswer(inv -> inv.getArgument(0));

        Category updated = service.update(3, "新名", null, null, 9);

        assertThat(updated.getName()).isEqualTo("新名");
        assertThat(updated.getIcon()).isEqualTo("old.png");
        assertThat(updated.getHasReview()).isTrue();
        assertThat(updated.getSortOrder()).isEqualTo(9);
    }

    @Test
    void updateAndDeleteReturn404ForUnknownCategory() {
        when(categoryRepository.findById(99)).thenReturn(Optional.empty());
        when(categoryRepository.existsById(99)).thenReturn(false);

        assertThatThrownBy(() -> service.update(99, "x", null, null, null))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(404);
        assertThatThrownBy(() -> service.delete(99))
                .isInstanceOf(BusinessException.class).extracting("code").isEqualTo(404);
    }

    @Test
    void deleteRefusesCategoryStillReferencedByTasks() {
        when(categoryRepository.existsById(3)).thenReturn(true);
        when(taskRepository.existsByCategory_Id(3)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(3))
                .isInstanceOf(BusinessException.class).hasMessage("该分类下仍有需求，不能删除")
                .extracting("code").isEqualTo(409);

        verify(categoryRepository, never()).deleteById(any());
    }

    @Test
    void deleteRemovesUnreferencedCategory() {
        when(categoryRepository.existsById(3)).thenReturn(true);
        when(taskRepository.existsByCategory_Id(3)).thenReturn(false);

        service.delete(3);

        verify(categoryRepository).deleteById(3);
    }
}
