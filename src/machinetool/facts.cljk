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

  These THREE entries (originally two -- `:unit/metal-additive-
  manufacturing-system` added alongside isic-2813's own AM-printed fan
  housing, see that repo's `resources/pressureequip/*.edn`) exist to
  give the superproject equipment-asset-linkage ADR
  (cloud-itonami-isic-2822<->cloud-itonami-isic-2813) a `:unit-type-id`
  for the machine tools THIS actor dispatches to isic-2813's own
  factory floor -- an industrial welding cell (for isic-2813's
  frame-weld station), a CNC machining center (for isic-2813's
  brazing/assembly stations), and a metal additive-manufacturing
  (industrial metal 3D-printing) system (for isic-2813's new
  `st:am-print` station, which prints the integrated condenser-fan
  housing/impeller `resources/pressureequip/compressor-unit-bom.edn`'s
  `part:am-fan-housing` line uses). Two classification fields on each
  entry come from EXTERNAL, independently-verifiable authorities and
  are reported HONESTLY per this fleet's anti-fabrication discipline
  (see ns docstring / ADR on UNSPSC/GTIN linkage in the superproject's
  `90-docs/adr/`):

    `:unspsc-code` -- an 8-digit UNSPSC (United Nations Standard
    Products and Services Code) code, UNSPSC segment 23 ('Industrial
    Manufacturing and Processing Machinery and Accessories', the same
    segment this actor's own machine-tool-manufacturing domain lives
    in). The first two entries below could not be confirmed at the
    specific 8-digit COMMODITY level via independent public
    commodity-code references (usa.databasesets.com class/family
    pages, an NCC-DOA/NASA-SEWP UNSPSC scope PDF cross-check) -- this
    namespace does NOT fabricate a more specific commodity code UNSPSC
    does not publicly confirm, so both stop at the CLASS level (6
    significant digits, zero-padded to 8, per this task's own explicit
    fallback allowance):
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

    `:unit/metal-additive-manufacturing-system` -> `\"23261505\"`, an
    actual 8-digit UNSPSC COMMODITY (not merely a class-level fallback)
    -- 'Selective laser sintering machine', class 23261500 'Rapid
    prototyping machines', family 23260000 'Rapid prototyping
    machinery and accessories', still within the SAME segment
    23000000 this catalog's other two entries live in. Confirmed via
    two independent sources: (1) usa.databasesets.com's own commodity
    page for 23261507 lists the full segment/family/class/commodity
    chain, and its sibling class page for 23261500 enumerates all
    seven commodities in that class (23261501 Fused deposition
    modeling machine / 23261502 Inkjet method machine / 23261503
    Laminated object manufacturing machine / 23261504 Laser powder
    forming machine / 23261505 Selective laser sintering machine /
    23261506 Stereolithography machine / 23261507 Three dimensional
    printing machine); (2) the SAME NASA SEWP UNSPSC scope PDF cited
    above independently lists segment 23000000 -> family 23260000
    'Rapid prototyping machinery and accessories' -> class 23261500
    'Rapid prototyping machines' verbatim. `23261505` ('Selective
    laser sintering machine') was chosen over the more generic
    `23261507` ('Three dimensional printing machine') because it names
    the SPECIFIC process this catalog entry declares
    (`:build-process :powder-bed-fusion`, see the entry's own
    docstring below for why PBF was chosen over DED) -- UNSPSC's
    'selective laser sintering' terminology predates the metal-AM
    industry's later 'laser powder bed fusion'/'DMLS'/'SLM' naming,
    but describes the IDENTICAL physical process (a laser selectively
    fuses successive layers of a powder bed) regardless of whether the
    powder is polymer or metal -- this is not a fabricated mapping,
    it is the taxonomy's own closest process-accurate commodity, and
    is reported here as an honest INTERPRETATION rather than an
    official UNSPSC metal-AM-specific code (UNSPSC segment 23260000 was
    seeded pre-metal-AM and has no separate 'metal' qualifier at the
    commodity level).

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
    :gtin/status :unissued-blueprint-placeholder}
   :unit/metal-additive-manufacturing-system
   {:id :unit/metal-additive-manufacturing-system
    :name "産業用金属積層造形システム"
    ;; Build process: Powder Bed Fusion (PBF, specifically laser-based
    ;; metal PBF -- DMLS/SLM/L-PBF are industry trade names for the
    ;; SAME underlying process class UNSPSC calls 'selective laser
    ;; sintering'), chosen over Directed Energy Deposition (DED)
    ;; because this system's job at isic-2813 is printing a small,
    ;; geometrically complex, thin-walled part (an integrated
    ;; condenser-fan housing/impeller, `part:am-fan-housing` --
    ;; see compressor-unit-bom.edn/compressor-unit-prod-order.edn)
    ;; where PBF's fine layer resolution and support for intricate
    ;; internal/overhang geometry is the industry-standard fit (e.g.
    ;; turbine/compressor impellers are a textbook L-PBF use case).
    ;; DED (laser/wire metal deposition) is comparatively suited to
    ;; large-scale near-net-shape builds, cladding and repair of
    ;; existing parts -- not this station's job -- so it was not
    ;; chosen here.
    :build-process :powder-bed-fusion
    :build-volume-mm [400.0 400.0 400.0]
    :materials ["SUS316L(ステンレス鋼)" "Inconel 718(ニッケル基超合金)" "AlSi10Mg(アルミ合金)"]
    :unspsc-code "23261505"
    :gtin "0212822000032"
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
