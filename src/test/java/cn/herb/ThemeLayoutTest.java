package cn.herb;

import cn.herb.view.Theme;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** 验证顶部导航改造依赖的三个 Swing 响应式宽度区间。 */
public final class ThemeLayoutTest {
    @Test
    void responsiveColumnsFollowWindowBands() {
        Theme.ResponsiveGrid grid = Theme.responsiveGrid(4, 2, 1, 16, 16);
        assertEquals(4, grid.columnsForWidth(1200));
        assertEquals(2, grid.columnsForWidth(1199));
        assertEquals(2, grid.columnsForWidth(900));
        assertEquals(1, grid.columnsForWidth(899));
        assertEquals(1, grid.columnsForWidth(760));
    }
}
