package io.github.noxaur.dsmod.edge.input

import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents
import net.fabricmc.fabric.api.event.Event
import net.fabricmc.fabric.api.event.player.AttackEntityCallback
import net.fabricmc.fabric.api.event.player.UseBlockCallback
import net.fabricmc.fabric.api.event.player.UseItemCallback
import net.minecraft.client.gui.screens.Screen

@InputApproved(hash = "e472cbf112ec")
object Refusals {
    fun allowKeyPress(screen: Screen): Event<ScreenKeyboardEvents.AllowKeyPress> = ScreenKeyboardEvents.allowKeyPress(screen)

    fun allowMouseClick(screen: Screen): Event<ScreenMouseEvents.AllowMouseClick> = ScreenMouseEvents.allowMouseClick(screen)

    fun allowMouseRelease(screen: Screen): Event<ScreenMouseEvents.AllowMouseRelease> = ScreenMouseEvents.allowMouseRelease(screen)

    fun allowMouseDrag(screen: Screen): Event<ScreenMouseEvents.AllowMouseDrag> = ScreenMouseEvents.allowMouseDrag(screen)

    fun allowMouseScroll(screen: Screen): Event<ScreenMouseEvents.AllowMouseScroll> = ScreenMouseEvents.allowMouseScroll(screen)

    fun useItem(): Event<UseItemCallback> = UseItemCallback.EVENT

    fun useBlock(): Event<UseBlockCallback> = UseBlockCallback.EVENT

    fun attackEntity(): Event<AttackEntityCallback> = AttackEntityCallback.EVENT
}
