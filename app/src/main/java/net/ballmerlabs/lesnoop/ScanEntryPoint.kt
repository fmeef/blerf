package net.ballmerlabs.lesnoop

import net.ballmerlabs.lesnoop.scan.Scanner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn

@EntryPoint
@InstallIn(ScanSubcomponent::class)
interface ScanEntryPoint {
    fun scanner(): Scanner
}