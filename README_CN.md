**简体中文** | [English](README.md)

# AttributeFix Sync (属性修复同步)

![License](https://img.shields.io/badge/license-MIT-blue)
![NeoForge](https://img.shields.io/badge/NeoForge-1.21.1-orange)

**AttributeFix Sync** 是 [AttributeFix](https://github.com/Darkhax-Minecraft/AttributeFix) 模组的一个非官方附属模组，旨在解决服务端修改属性上限后，客户端显示不同步的问题。

## 问题背景

在原版 **AttributeFix** 中，如果你在服务端配置文件中修改了某个属性（例如 `generic.max_health` 或 `generic.attack_damage`）的上限：
1.  **服务端**会正确计算伤害和血量（例如上限 5000）。
2.  **客户端**仍然使用默认的上限（例如 1000000）。
3.  **结果**：导致 Jade、WTHIT 或原版血条/属性显示错误，玩家看到的数值不能被正确截断，与实际数值不符。

## 本模组的功能

本模组通过网络包同步机制解决了上述问题：

*   **自动同步**：玩家进入服务器时，模组会自动将服务端配置好的所有属性上限（Max Value）发送给客户端。
*   **内存修正**：客户端接收数据后，会通过 Mixin 动态修改本地内存中的属性定义。
*   **完美显示**：Jade、WTHIT 以及原版血条现在能正确显示超出原版限制的数值。
*   **无污染**：当玩家退出服务器时，模组会自动还原客户端的属性上限到初始状态，不会影响单人游戏或其他服务器。

## 安装与使用

### 依赖关系
*   **Minecraft**: 1.21.1
*   **Loader**: NeoForge
*   **前置模组**: [AttributeFix](https://www.curseforge.com/minecraft/mc-mods/attributefix)

### 如何使用
1.  **服务端**：必须安装。安装后无需配置，它会自动读取 AttributeFix 的最终数值并发送给玩家。
2.  **客户端**：必须安装。否则无法接收同步数据包。

## 配置文件

本模组**没有**配置文件。
它直接读取服务端上 `AttributeFix` 已经生效的数值。如果你需要修改属性上限，请修改 `AttributeFix` 原本生成的配置文件（位于 `config/attributefix/` 下）。

## 许可证与致谢

本项目采用 **MIT License** 开源。

*   核心逻辑基于 Minecraft NeoForge API。
*   感谢 **Darkhax** 开发了原本的 [AttributeFix](https://www.curseforge.com/minecraft/mc-mods/attributefix) 模组，本模组仅作为其补充工具存在。

---
*这不是 Darkhax 官方的作品。如有同步相关的问题，请在本仓库提交 Issue，不要去打扰原作者。*