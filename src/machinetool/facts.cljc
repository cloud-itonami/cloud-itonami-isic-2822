(ns machinetool.facts
  "Per-jurisdiction machine-tool design/accuracy-conformity catalog --
  the G2-style spec-basis table the Machine Tool Governor checks every
  `:design-rules/verify` proposal against.

  Coverage is reported HONESTLY: a jurisdiction not in this table has
  NO spec-basis. Seed values cite official machine-tool safety /
  conformity authorities and reference ISO 230 (geometric and
  positioning accuracy testing of machine tools) as the shared
  international baseline every one of the four jurisdictions'
  national standards adopts or points to; this is a starting catalog,
  not a survey of every market.")

(def catalog
  {"JPN" {:name "Japan"
          :owner-authority "経済産業省 (METI) / 日本工作機械工業会 (JMTBA) / 日本産業規格 (JIS)"
          :legal-basis "労働安全衛生法 / 機械等の包括的な安全基準に関する指針 / JIS B 6201・B 6336 (ISO 230 整合、参考)"
          :national-spec "工作機械の幾何精度・位置決め精度試験および安全設計要件"
          :provenance "https://www.meti.go.jp/"
          :required-evidence ["幾何精度試験報告書 (geometric-accuracy-test-report)"
                              "位置決め精度試験報告書 (positioning-accuracy-test-report)"
                              "機械ガード適合記録 (machine-guarding-conformity-record)"
                              "材料証明記録 (material-certification-record)"]}
   "USA" {:name "United States"
          :owner-authority "ANSI / ASME (B5 committee) / OSHA"
          :legal-basis "ANSI/ASME B5.54 / B5.57 machine-tool accuracy test codes (reference, ISO 230-aligned) / 29 CFR 1910.212 (Machine Guarding)"
          :national-spec "US machine-tool accuracy-test and guarding conformity requirements"
          :provenance "https://www.ansi.org/"
          :required-evidence ["geometric-accuracy-test-report"
                              "positioning-accuracy-test-report"
                              "machine-guarding-conformity-record"
                              "Material-certification-record"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "HSE / UKCA machinery framework"
          :legal-basis "Supply of Machinery (Safety) Regulations 2008 (as retained) / BS EN ISO 230 adoption (reference)"
          :national-spec "UK machine-tool conformity and guarding requirements"
          :provenance "https://www.hse.gov.uk/"
          :required-evidence ["geometric-accuracy-test-report"
                              "positioning-accuracy-test-report"
                              "machine-guarding-conformity-record"
                              "Material-certification-record"]}
   "DEU" {:name "Germany"
          :owner-authority "BAuA / DIN / EU-Maschinenrichtlinie-Kontext"
          :legal-basis "Maschinenrichtlinie 2006/42/EG (künftig Maschinenverordnung (EU) 2023/1230) / DIN EN ISO 230 / DIN EN ISO 16090 (Referenz)"
          :national-spec "DE Werkzeugmaschinen-Genauigkeitsprüfung und CE-Konformitätsanforderungen"
          :provenance "https://www.din.de/"
          :required-evidence ["Geometrieprüfbericht (geometric-accuracy-test-report)"
                              "Positioniergenauigkeitsprüfbericht (positioning-accuracy-test-report)"
                              "Schutzeinrichtungs-Konformitätsnachweis (machine-guarding-conformity-record)"
                              "Werkstoffzertifikat (material-certification-record)"]}})

(defn spec-basis [iso3] (get catalog iso3))

(defn coverage
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-2822 R0: " (count catalog)
                 " jurisdictions seeded. Extend `machinetool.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
