package com.invalid.lesnoop

import com.invalid.lesnoop.scan.Scanner
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn

@EntryPoint
@InstallIn(ScanSubcomponent::class)
interface ScanEntryPoint {
    fun scanner(): Scanner
}