package com.example.data

object LsrChecklist {
    val items = listOf(
        // Working at Height (WAH)
        LsrItem(
            id = "LSR-WAH-01",
            category = "Bekerja di Ketinggian",
            category_en = "Working at Height",
            item_text = "Apakah semua pekerja di ketinggian (>1.8m) telah menggunakan Full Body Harness dengan double lanyard yang dicantolkan pada anchor point yang kokoh (kapasitas min. 22 kN)?",
            item_text_en = "Are all personnel working at heights (>1.8m) wearing a 100% tied-off Full Body Harness attached to a certified anchorage point (min. 22 kN capacity)?",
            standard_ref = "OSHA 1926.501, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-WAH-02",
            category = "Bekerja di Ketinggian",
            category_en = "Working at Height",
            item_text = "Apakah perancah (scaffolding) telah diinspeksi oleh Scaffolder bersertifikat dan terpasang tag hijau (Safe for Use) sebelum digunakan?",
            item_text_en = "Has scaffolding been inspected by a certified inspector and fitted with a valid green tag ('Safe for Use') prior to access?",
            standard_ref = "OSHA 1926.451, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-WAH-03",
            category = "Bekerja di Ketinggian",
            category_en = "Working at Height",
            item_text = "Apakah dipasang pagar pengaman (guardrail) standar yang terdiri dari top rail, mid rail, dan toe board di sekeliling area kerja terbuka?",
            item_text_en = "Are standard guardrails (top-rail, mid-rail, and toeboard) installed on all open elevated working edges?",
            standard_ref = "OSHA 1926.502, IOGP LSR",
            is_critical = false
        ),
        LsrItem(
            id = "LSR-WAH-04",
            category = "Bekerja di Ketinggian",
            category_en = "Working at Height",
            item_text = "Apakah semua peralatan kerja di ketinggian diikat dengan lanyard alat (tool lanyard) untuk mencegah bahaya benda jatuh (dropped objects)?",
            item_text_en = "Are all tools and loose equipment tethered with tool lanyards / pouches to prevent dropped object hazards?",
            standard_ref = "OSHA 1926.759, IOGP LSR",
            is_critical = false
        ),
        LsrItem(
            id = "LSR-WAH-05",
            category = "Bekerja di Ketinggian",
            category_en = "Working at Height",
            item_text = "Apakah area di bawah pekerjaan ketinggian telah diisolasi (barikade dan tanda peringatan) untuk mencegah personel melintas di bawahnya?",
            item_text_en = "Is the drop zone beneath elevated works barricaded with danger signage to prevent personnel entry?",
            standard_ref = "OSHA 1926.502, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-WAH-06",
            category = "Bekerja di Ketinggian",
            category_en = "Working at Height",
            item_text = "Apakah rencana tanggap darurat penyelamatan ketinggian (Rescue Plan) telah tersedia, disosialisasikan, dan peralatan rescue siap pakai di lokasi?",
            item_text_en = "Is an emergency working-at-height rescue plan documented, briefed to the team, and rescue kits ready on site?",
            standard_ref = "OSHA 1926.502, IOGP LSR",
            is_critical = true
        ),

        // Confined Space Entry (CSE)
        LsrItem(
            id = "LSR-CSE-01",
            category = "Ruang Terbatas",
            category_en = "Confined Space Entry",
            item_text = "Apakah izin kerja masuk ruang terbatas (Confined Space Entry Permit) telah disetujui dan seluruh isolasi fisik (blind/spectacle) telah dipasang?",
            item_text_en = "Is the Confined Space Entry permit approved and are all mechanical/process connections positively isolated (blinded)?",
            standard_ref = "OSHA 1910.146, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-CSE-02",
            category = "Ruang Terbatas",
            category_en = "Confined Space Entry",
            item_text = "Apakah telah dilakukan pengukuran gas atmosfer berbahaya (Oksigen 19.5%-23.5%, LEL <10%, Gas Beracun <TLV) oleh Authorized Gas Tester (AGT)?",
            item_text_en = "Has atmospheric gas testing (Oxygen 19.5-23.5%, LEL <10%, Toxic gases <TLV) been verified by an Authorized Gas Tester immediately prior to entry?",
            standard_ref = "OSHA 1910.146, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-CSE-03",
            category = "Ruang Terbatas",
            category_en = "Confined Space Entry",
            item_text = "Apakah petugas jaga ruang terbatas (Confined Space Attendant/Standby Person) bersertifikat telah siaga penuh di luar akses masuk?",
            item_text_en = "Is a qualified Standby Person / Entry Attendant stationed stationed outside the entrance at all times?",
            standard_ref = "OSHA 1910.146, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-CSE-04",
            category = "Ruang Terbatas",
            category_en = "Confined Space Entry",
            item_text = "Apakah sistem ventilasi paksa (mechanical ventilation/blower) dinyalakan terus-menerus selama pekerjaan berlangsung dan udara dipantau periodik?",
            item_text_en = "Is continuous forced mechanical ventilation operating and continuous atmospheric monitoring in place?",
            standard_ref = "OSHA 1910.146, IOGP LSR",
            is_critical = false
        ),
        LsrItem(
            id = "LSR-CSE-05",
            category = "Ruang Terbatas",
            category_en = "Confined Space Entry",
            item_text = "Apakah peralatan penyelamatan darurat (tripod rescue, harness, breathing apparatus) telah disiapkan di dekat pintu masuk?",
            item_text_en = "Is emergency retrieval equipment (rescue tripod, winch, harness, emergency breathing gear) positioned at the access point?",
            standard_ref = "OSHA 1910.146, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-CSE-06",
            category = "Ruang Terbatas",
            category_en = "Confined Space Entry",
            item_text = "Apakah sistem komunikasi dua arah antara pekerja di dalam dan attendant di luar telah diuji dan berfungsi dengan baik?",
            item_text_en = "Has positive two-way communication between entrants and the standby attendant been tested and established?",
            standard_ref = "OSHA 1910.146, IOGP LSR",
            is_critical = false
        ),

        // Energy Isolation / LOTO
        LsrItem(
            id = "LSR-LTO-01",
            category = "Isolasi Energi / LOTO",
            category_en = "Energy Isolation / LOTO",
            item_text = "Apakah isolasi energi berbahaya telah dikonfirmasi menggunakan metode LOTO (Lockout, Tagout) pada titik isolasi utama sebelum pekerjaan dimulai?",
            item_text_en = "Has hazardous energy isolation been confirmed using Lockout/Tagout (LOTO) at all primary isolation points before work?",
            standard_ref = "OSHA 1910.147, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-LTO-02",
            category = "Isolasi Energi / LOTO",
            category_en = "Energy Isolation / LOTO",
            item_text = "Apakah uji ketiadaan energi (Zero Energy State Test / Try Out) telah dilakukan untuk memastikan tidak ada sisa energi (residual energy) yang terperangkap?",
            item_text_en = "Has a Zero Energy State test (Try Out / Bleed / Test) been executed to verify no residual energy remains?",
            standard_ref = "OSHA 1910.147, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-LTO-03",
            category = "Isolasi Energi / LOTO",
            category_en = "Energy Isolation / LOTO",
            item_text = "Apakah setiap pekerja yang terlibat telah memasang gembok pengaman pribadi (Personal Lock) mereka masing-masing pada hasp isolasi?",
            item_text_en = "Has every individual worker affixed their own Personal Safety Lock to the multi-lock hasp/lockbox?",
            standard_ref = "OSHA 1910.147, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-LTO-04",
            category = "Isolasi Energi / LOTO",
            category_en = "Energy Isolation / LOTO",
            item_text = "Apakah dokumen daftar isolasi (LOTO Register/Isolation List) telah diverifikasi dan ditandatangani oleh Isolator dan Pengawas Pekerjaan?",
            item_text_en = "Has the isolation schedule / certificate been validated and signed by the Authorized Isolator and Performing Authority?",
            standard_ref = "OSHA 1910.147, IOGP LSR",
            is_critical = false
        ),
        LsrItem(
            id = "LSR-LTO-05",
            category = "Isolasi Energi / LOTO",
            category_en = "Energy Isolation / LOTO",
            item_text = "Apakah sistem pelepasan tekanan (depressurization/drainage) telah dilakukan secara aman sebelum membuka katup atau flange?",
            item_text_en = "Have piping/vessel pressure, stored liquids, and chemicals been fully depressurized and drained prior to intervention?",
            standard_ref = "OSHA 1910.147, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-LTO-06",
            category = "Isolasi Energi / LOTO",
            category_en = "Energy Isolation / LOTO",
            item_text = "Apakah batas zona aman isolasi telah diidentifikasi dan ditandai secara fisik di lapangan agar tidak ada intervensi dari luar?",
            item_text_en = "Are the physical boundaries of the isolated system clearly tagged and barricaded against unauthorized tamper?",
            standard_ref = "OSHA 1910.147, IOGP LSR",
            is_critical = false
        ),

        // Safe Mechanical Lifting (SML)
        LsrItem(
            id = "LSR-SML-01",
            category = "Pengangkatan Mekanis",
            category_en = "Safe Mechanical Lifting",
            item_text = "Apakah rencana pengangkatan (Lifting Plan) yang sesuai kelas beban (rutin/kritis) telah disusun, disetujui, dan disosialisasikan?",
            item_text_en = "Is a certified Lifting Plan prepared, approved for the specific lift category (routine vs. critical), and briefed in toolbox meeting?",
            standard_ref = "OSHA 1926.1400, ASME B30.5",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-SML-02",
            category = "Pengangkatan Mekanis",
            category_en = "Safe Mechanical Lifting",
            item_text = "Apakah Operator Crane, Rigger, dan Signalman memiliki sertifikat kompetensi (SIO) resmi yang masih berlaku?",
            item_text_en = "Are the Crane Operator, Riggers, and Banksman/Signalman certified with valid competency licenses?",
            standard_ref = "OSHA 1926.1427, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-SML-03",
            category = "Pengangkatan Mekanis",
            category_en = "Safe Mechanical Lifting",
            item_text = "Apakah semua alat bantu angkat (lifting gears seperti shackle, sling, webbing) telah diinspeksi secara visual dan memiliki sertifikat uji riksa?",
            item_text_en = "Have all rigging gears (shackles, wire rope slings, synthetic webbings) been visually inspected and validly color-coded?",
            standard_ref = "OSHA 1926.251, ASME B30.9",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-SML-04",
            category = "Pengangkatan Mekanis",
            category_en = "Safe Mechanical Lifting",
            item_text = "Apakah perangkat pengaman crane (seperti Load Moment Indicator - LMI, anti-two block) berfungsi dengan baik dan tidak di-bypass?",
            item_text_en = "Are crane safety safety devices (LMI, Anti-Two Block, load charts, anemometer) operational and never bypassed?",
            standard_ref = "OSHA 1926.1412, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-SML-05",
            category = "Pengangkatan Mekanis",
            category_en = "Safe Mechanical Lifting",
            item_text = "Apakah barikade radius putar crane (swing radius) telah dipasang penuh dan dipastikan tidak ada orang berada di bawah beban gantung?",
            item_text_en = "Is the crane swing radius barricaded and is standing or walking beneath a suspended load strictly prevented?",
            standard_ref = "OSHA 1926.1424, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-SML-06",
            category = "Pengangkatan Mekanis",
            category_en = "Safe Mechanical Lifting",
            item_text = "Apakah kondisi tanah tempat penempatan outrigger crane telah diperiksa, stabil, dan menggunakan outrigger pad/timber yang memadai?",
            item_text_en = "Is the crane ground condition verified solid and outrigger mats/timbers properly deployed to distribute weight?",
            standard_ref = "OSHA 1926.1402, ASME B30.5",
            is_critical = false
        ),

        // Excavation & Trenching (EXC)
        LsrItem(
            id = "LSR-EXC-01",
            category = "Pekerjaan Galian",
            category_en = "Excavation & Trenching",
            item_text = "Apakah galian dengan kedalaman >1.2 meter telah dipasang sistem proteksi dinding galian (shoring, shielding, atau sloping) untuk mencegah longsor?",
            item_text_en = "Are excavations >1.2m deep equipped with cave-in protection systems (shoring, trench box shield, or angle of repose sloping)?",
            standard_ref = "OSHA 1926.652, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-EXC-02",
            category = "Pekerjaan Galian",
            category_en = "Excavation & Trenching",
            item_text = "Apakah deteksi utilitas bawah tanah (underground utility detection) seperti kabel listrik/pipa gas telah dilakukan sebelum penggalian dimulai?",
            item_text_en = "Has an underground utility survey (cable/pipe locator scan) and digging clearance permit been completed before ground penetration?",
            standard_ref = "OSHA 1926.651, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-EXC-03",
            category = "Pekerjaan Galian",
            category_en = "Excavation & Trenching",
            item_text = "Apakah akses masuk/keluar yang aman (seperti tangga) tersedia dalam jarak maksimal 7.5 meter dari pekerja di dalam galian?",
            item_text_en = "Are safe entry/egress ladders placed within a maximum lateral distance of 7.5 meters (25 ft) from any worker?",
            standard_ref = "OSHA 1926.651, IOGP LSR",
            is_critical = false
        ),
        LsrItem(
            id = "LSR-EXC-04",
            category = "Pekerjaan Galian",
            category_en = "Excavation & Trenching",
            item_text = "Apakah material hasil galian (spoil pile) dan alat berat ditempatkan minimal 0.6 meter dari bibir galian untuk menghindari beban runtuh?",
            item_text_en = "Are excavated spoil piles and heavy equipment set back at least 0.6 meters (2 ft) from the trench edge?",
            standard_ref = "OSHA 1926.651, IOGP LSR",
            is_critical = false
        ),
        LsrItem(
            id = "LSR-EXC-05",
            category = "Pekerjaan Galian",
            category_en = "Excavation & Trenching",
            item_text = "Apakah atmosfer di dalam galian dalam (>1.2m) diuji secara berkala untuk memastikan tidak ada akumulasi gas berbahaya atau kekurangan oksigen?",
            item_text_en = "Is the atmosphere tested regularly in deep excavations where hazardous gas accumulation or oxygen deficiency is possible?",
            standard_ref = "OSHA 1926.651, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-EXC-06",
            category = "Pekerjaan Galian",
            category_en = "Excavation & Trenching",
            item_text = "Apakah barikade fisik yang kokoh dan lampu penerangan/reflektor telah dipasang di sekeliling area galian?",
            item_text_en = "Are rigid physical barricades and reflective warning lights placed around the excavation perimeter to prevent falls?",
            standard_ref = "OSHA 1926.651, IOGP LSR",
            is_critical = true
        ),

        // Hot Work (HOT)
        LsrItem(
            id = "LSR-HOT-01",
            category = "Pekerjaan Panas",
            category_en = "Hot Work",
            item_text = "Apakah izin kerja panas (Hot Work Permit) telah disetujui dan area kerja bebas dari material mudah terbakar dalam radius minimal 11 meter (35 kaki)?",
            item_text_en = "Is a Hot Work Permit validated and combustible materials removed or protected within an 11-meter (35-foot) radius?",
            standard_ref = "OSHA 1910.252, NFPA 51B",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-HOT-02",
            category = "Pekerjaan Panas",
            category_en = "Hot Work",
            item_text = "Apakah Fire Watcher (Petugas Jaga Api) yang terlatih bersiap di lokasi kerja dengan APAR (Alat Pemadam Api Ringan) siap pakai?",
            item_text_en = "Is a dedicated Fire Watch stationed with an inspected fire extinguisher on site during and 30 minutes after hot work?",
            standard_ref = "OSHA 1910.252, NFPA 51B",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-HOT-03",
            category = "Pekerjaan Panas",
            category_en = "Hot Work",
            item_text = "Apakah tabung gas oksigen dan asetilen dilengkapi dengan Flashback Arrestor pada kedua ujung regulator dan obor las?",
            item_text_en = "Are oxygen and acetylene cylinders fitted with verified flashback arrestors at both the regulators and torches?",
            standard_ref = "OSHA 1910.253, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-HOT-04",
            category = "Pekerjaan Panas",
            category_en = "Hot Work",
            item_text = "Apakah peralatan listrik atau mesin las telah di-grounding dengan benar dan kabel-kabelnya bebas dari sambungan terkelupas?",
            item_text_en = "Is the welding machine properly grounded and all electrical welding cables free from splices, fraying, or damage?",
            standard_ref = "OSHA 1910.303, IOGP LSR",
            is_critical = false
        ),
        LsrItem(
            id = "LSR-HOT-05",
            category = "Pekerjaan Panas",
            category_en = "Hot Work",
            item_text = "Apakah dilakukan pengujian gas atmosfer (combustible gas monitoring) berkala sebelum dan selama pengelasan/pemotongan?",
            item_text_en = "Is continuous or periodic flammable gas monitoring conducted throughout hot work in hazardous zones?",
            standard_ref = "OSHA 1910.252, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-HOT-06",
            category = "Pekerjaan Panas",
            category_en = "Hot Work",
            item_text = "Apakah tirai las tahan api (fire blanket) dipasang rapat di sekeliling area pekerjaan untuk menahan percikan bunga api?",
            item_text_en = "Are approved fire blankets / containment spark containment screens placed around welding to trap sparks and slag?",
            standard_ref = "OSHA 1910.252, NFPA 51B",
            is_critical = false
        ),

        // Line Breaking / Bypass Safety Controls (BYP)
        LsrItem(
            id = "LSR-BYP-01",
            category = "Pembukaan Jalur & Bypass",
            category_en = "Line Breaking & System Bypass",
            item_text = "Apakah pembukaan jalur pipa (Line Breaking) yang berisi zat berbahaya telah diisolasi penuh, didepresurisasi, dibilas (flushed), dan diuji residunya?",
            item_text_en = "Has hazardous process line breaking been positively isolated, depressurized, drained, flushed, and tested for residual chemicals?",
            standard_ref = "OSHA 1910.119, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-BYP-02",
            category = "Pembukaan Jalur & Bypass",
            category_en = "Line Breaking & System Bypass",
            item_text = "Apakah tindakan membypass sistem pengaman (safety controls bypass) seperti interlock, ESD, atau alarm keselamatan telah disetujui formal?",
            item_text_en = "Has formal management authorization been approved prior to overriding or bypassing critical safety devices (ESD, PSV, interlocks)?",
            standard_ref = "OSHA 1910.119, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-BYP-03",
            category = "Pembukaan Jalur & Bypass",
            category_en = "Line Breaking & System Bypass",
            item_text = "Apakah pekerja menggunakan APD spesifik bahan kimia (chemical suit, respiratory protection/face shield) yang tepat saat melakukan line breaking?",
            item_text_en = "Are technicians donning appropriate chemical-resistant PPE (splash suits, full face shields, respirator) during initial line breach?",
            standard_ref = "OSHA 1910.132, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-BYP-04",
            category = "Pembukaan Jalur & Bypass",
            category_en = "Line Breaking & System Bypass",
            item_text = "Apakah mitigasi risiko alternatif (alternative mitigation) telah dipasang dan dipantau aktif selama sistem pengaman kritis dinonaktifkan?",
            item_text_en = "Are robust alternative risk mitigation measures implemented and continuously monitored while safety controls are inhibited?",
            standard_ref = "OSHA 1910.119, IOGP LSR",
            is_critical = true
        ),
        LsrItem(
            id = "LSR-BYP-05",
            category = "Pembukaan Jalur & Bypass",
            category_en = "Line Breaking & System Bypass",
            item_text = "Apakah penandaan fisik (labeling/lock) pada katup yang dibuka/ditutup telah dipasang untuk mencegah pengoperasian tidak disengaja?",
            item_text_en = "Are valve position locks and conspicuous caution tags applied to prevent inadvertent operation during maintenance?",
            standard_ref = "OSHA 1910.147, IOGP LSR",
            is_critical = false
        ),
        LsrItem(
            id = "LSR-BYP-06",
            category = "Pembukaan Jalur & Bypass",
            category_en = "Line Breaking & System Bypass",
            item_text = "Apakah sistem pengaman yang dinonaktifkan terdaftar dalam log bypass dan dilakukan uji fungsionalitas sebelum dioperasikan kembali?",
            item_text_en = "Is the inhibited system logged in the formal Bypass Register and function-tested prior to final re-commissioning?",
            standard_ref = "OSHA 1910.119, IOGP LSR",
            is_critical = false
        )
    )

    fun getCategoryIcon(category: String): String {
        return when {
            category.contains("Ketinggian", ignoreCase = true) || category.contains("Height", ignoreCase = true) -> "🪜"
            category.contains("Panas", ignoreCase = true) || category.contains("Hot", ignoreCase = true) -> "🔥"
            category.contains("Terbatas", ignoreCase = true) || category.contains("Confined", ignoreCase = true) -> "🕳️"
            category.contains("Energi", ignoreCase = true) || category.contains("LOTO", ignoreCase = true) || category.contains("Energy", ignoreCase = true) -> "⚡"
            category.contains("Angkat", ignoreCase = true) || category.contains("Lifting", ignoreCase = true) -> "🏗️"
            category.contains("Galian", ignoreCase = true) || category.contains("Excavation", ignoreCase = true) -> "⛏️"
            category.contains("Mengemudi", ignoreCase = true) || category.contains("Driving", ignoreCase = true) -> "🚗"
            category.contains("Bypass", ignoreCase = true) || category.contains("Line", ignoreCase = true) -> "⚙️"
            else -> "🛡️"
        }
    }
}
