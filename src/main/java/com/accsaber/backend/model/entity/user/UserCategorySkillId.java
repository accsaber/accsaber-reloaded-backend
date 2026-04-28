package com.accsaber.backend.model.entity.user;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserCategorySkillId implements Serializable {

    private Long user;
    private UUID category;

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof UserCategorySkillId other))
            return false;
        return Objects.equals(user, other.user) && Objects.equals(category, other.category);
    }

    @Override
    public int hashCode() {
        return Objects.hash(user, category);
    }
}
