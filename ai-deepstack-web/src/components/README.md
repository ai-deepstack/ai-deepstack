# 前端表单控件

统一使用 `src/components/Prop*` 系列，避免页面里直接写原生 `select` / `checkbox` / `radio` / `number` / `date`。

| 组件 | 用途 |
|------|------|
| `PropSelect` | 单选下拉 |
| `PropMultiSelect` | 多选下拉 |
| `PropCheckbox` | 单个勾选（支持 `trueValue`/`falseValue`，如 1/0） |
| `PropCheckboxGroup` | 勾选列表 |
| `PropRadio` | 单选组（`direction`: column/row） |
| `PropNumber` | 数字（`nullable` 空值发 null） |
| `PropDateTime` | 日期/时间（`type`: date / time / datetime） |

视觉样式集中在 `prop-controls.css`，与 `PropSelect` 一致。
