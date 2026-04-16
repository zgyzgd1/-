import os

filepath = "app/src/main/java/com/example/timetable/ui/ScheduleScreen.kt"
with open(filepath, "r", encoding="utf-8") as f:
    lines = f.readlines()

def get_lines(start, end):
    return "".join(lines[start-1:end])

hero_imports = """package com.example.timetable.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
"""

dialogs_imports = """package com.example.timetable.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.timetable.data.*
"""

calendar_imports = """package com.example.timetable.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.timetable.data.*
import java.time.LocalDate
import java.time.YearMonth
"""

cards_imports = """package com.example.timetable.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.timetable.data.*
"""

def write_component(name, imports, line_ranges):
    content = imports + "\n"
    for (start, end) in line_ranges:
        content += get_lines(start, end)
    content = content.replace("private fun ", "fun ")
    with open(f"app/src/main/java/com/example/timetable/ui/{name}.kt", "w", encoding="utf-8") as f:
        f.write(content)

write_component("TimetableHero", hero_imports, [(397, 490), (677, 693)])
write_component("TimetableDialogs", dialogs_imports, [(492, 539), (799, 969)])
write_component("TimetableCalendar", calendar_imports, [(541, 675)])
write_component("TimetableCards", cards_imports, [(695, 797)])

new_lines = lines[:396] 
with open(filepath, "w", encoding="utf-8") as f:
    f.writelines(new_lines)
