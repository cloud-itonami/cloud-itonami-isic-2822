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

(def unit-types
  "Catalog of concrete manufactured UNIT MODELS this actor's units can
  declare a `:unit-type-id` reference to (a SEPARATE catalog from
  `catalog` above, which is per-JURISDICTION regulatory evidence, not
  per-PRODUCT spec data -- this catalog does not replace or alter
  `catalog`). Same shape as cloud-itonami-isic-2813's own
  `pressureequip.facts/unit-types` (no shared code -- this actor's own
  independent copy of the same catalog convention).

  These two entries exist to give the superproject equipment-asset-
  linkage ADR (cloud-itonami-isic-2822<->cloud-itonami-isic-2813) a
  `:unit-type-id` for the machine tools THIS actor dispatches to
  isic-2813's own factory floor -- an industrial welding cell (for
  isic-2813's frame-weld station) and a CNC machining center (for
  isic-2813's brazing/assembly stations). Two classification fields on
  each entry come from EXTERNAL, independently-verifiable authorities
  and are reported HONESTLY per this fleet's anti-fabrication
  discipline (see ns docstring / ADR on UNSPSC/GTIN linkage in the
  superproject's `90-docs/adr/`):

    `:unspsc-code` -- an 8-digit UNSPSC (United Nations Standard
    Products and Services Code) code, UNSPSC segment 23 ('Industrial
    Manufacturing and Processing Machinery and Accessories', the same
    segment this actor's own machine-tool-manufacturing domain lives
    in). Neither entry below could be confirmed at the specific 8-digit
    COMMODITY level via independent public commodity-code references
    (usa.databasesets.com class/family pages, an NCC-DOA/NASA-SEWP
    UNSPSC scope PDF cross-check) -- this namespace does NOT fabricate
    a more specific commodity code UNSPSC does not publicly confirm,
    so both entries stop at the CLASS level (6 significant digits,
    zero-padded to 8, per this task's own explicit fallback allowance):
      - `:unit/industrial-welding-cell` -> `\"23271400\"`, UNSPSC class
        'Welding machinery', family 23270000 'Welding and soldering
        and brazing machinery and accessories and supplies'.
      - `:unit/cnc-machining-center` -> `\"23242400\"`, UNSPSC class
        'Machining centers', family 23240000 'Metal cutting machinery
        and accessories'.
    Both families/classes were independently confirmed via multiple
    consistent usa.databasesets.com search results, and the parent
    segment (23000000, 'Industrial Manufacturing and Processing
    Machinery and Accessories') plus the 23240000 family title were
    independently cross-checked directly against NASA SEWP's own
    published UNSPSC scope PDF (sewp.nasa.gov/documents/
    Website_UNSPSC_Scope_080824.pdf).

    `:gtin` -- a GTIN (Global Trade Item Number) is NOT a
    classification taxonomy code at all -- it is an identifier GS1
    issues per REGISTERED PHYSICAL PRODUCT, only after a real company
    enrolls with GS1 and assigns it. Like `pressureequip.facts/unit-
    types`, every `:gtin` value here is a SYNTACTICALLY VALID but
    NEVER-ISSUED placeholder: built on GS1's own officially-documented
    'Restricted Circulation Number' (RCN) prefix '021' (the SAME
    020-029 reserved-for-internal-use range isic-2813's own catalog
    uses), encoding this actor's own ISIC code (2822) plus a sequence,
    with a correctly computed Modulo-10 GTIN-13 check digit (verified
    against the standard EAN-13 worked example 400638133393->1, the
    SAME worked example isic-2813's own catalog cites). The sibling key
    `:gtin/status :unissued-blueprint-placeholder` makes the
    non-issuance explicit and machine-checkable; treat `:gtin` here as
    an EXAMPLE VALUE ONLY, never as a real, GS1-issued identifier for
    an actual unit."
  {:unit/industrial-welding-cell
   {:id :unit/industrial-welding-cell
    :name "産業用溶接セル(溶接ロボット+溶接機)"
    :unspsc-code "23271400"
    :gtin "0212822000018"
    :gtin/status :unissued-blueprint-placeholder}
   :unit/cnc-machining-center
   {:id :unit/cnc-machining-center
    :name "CNCマシニングセンタ"
    :unspsc-code "23242400"
    :gtin "0212822000025"
    :gtin/status :unissued-blueprint-placeholder}})

(defn unit-type-by-id [id]
  (get unit-types id))

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
