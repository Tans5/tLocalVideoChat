package com.tans.tlocalvideochat

import java.text.DecimalFormat


class ModifyNumber {

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            // origin width: 960, height: 960
            val targetString = "M320,560L560,560Q577,560 588.5,548.5Q600,537 600,520L600,440L663,503Q668,508 674,505.5Q680,503 680,496L680,304Q680,297 674,294.5Q668,292 663,297L600,360L600,280Q600,263 588.5,251.5Q577,240 560,240L320,240Q303,240 291.5,251.5Q280,263 280,280L280,520Q280,537 291.5,548.5Q303,560 320,560ZM240,720L148,812Q129,831 104.5,820.5Q80,810 80,783L80,160Q80,127 103.5,103.5Q127,80 160,80L800,80Q833,80 856.5,103.5Q880,127 880,160L880,640Q880,673 856.5,696.5Q833,720 800,720L240,720ZM206,640L800,640Q800,640 800,640Q800,640 800,640L800,160Q800,160 800,160Q800,160 800,160L160,160Q160,160 160,160Q160,160 160,160L160,685L206,640ZM160,640L160,640L160,160Q160,160 160,160Q160,160 160,160L160,160Q160,160 160,160Q160,160 160,160L160,640Q160,640 160,640Q160,640 160,640Z"
            val regex = "([0-9.]+)".toRegex()
            val result = regex.findAll(targetString).toList()
            val outputFormat = DecimalFormat("#.#")
            val scale = 108.0f / 960.0f * 0.5f
            val offset = 27.5f
            // fixed width: 108, height: 108
            val fixedString = StringBuilder()
            var startIndex = 0
            for (g in result) {
                val newValue = g.value.toFloat() * scale + offset
                val replaceString = outputFormat.format(newValue)
                fixedString.append(targetString.substring(startIndex until g.range.first))
                startIndex = g.range.last + 1
                fixedString.append(replaceString)
            }
            fixedString.append(targetString.substring(startIndex until targetString.length))
            println(fixedString.toString())
        }
    }
}