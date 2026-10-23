# ViaMe — Intelligent Multi-Role Urban Transport & Taxi Booking System

[![Java Version](https://img.shields.io/badge/Java-17%2B-orange.svg)](https://www.oracle.com/java/)
[![JavaFX](https://img.shields.io/badge/JavaFX-17%2B-blue.svg)](https://openjfx.io/)
[![MySQL](https://img.shields.io/badge/MySQL-8.0%2B-00758F.svg)](https://www.mysql.com/)
[![Academic Project](https://img.shields.io/badge/ISJ-Java%20Programming%20II-green.svg)](https://saintjeaningenieur.org/)

**ViaMe** is an enterprise-grade, multi-role desktop simulation of an urban taxi and ride dispatching platform developed for **Institut Saint Jean (ISJ)**, Academic Year 2026/2027.

---

## 👥 Engineering Team & Role Allocation (Group of 4)

| Role | Engineer | Focus & Core Responsibilities |
| :--- | :--- | :--- |
| **Student 1: System Analyst & Database Engineer** | *Team Member 1* | Requirements analysis, 10 use case specifications, conceptual ERD, 3NF normalization, and physical MySQL schema. |
| **Student 2: Software Architect & Backend Core Engineer** | *Team Member 2* | 5-tier layered architecture, core OOP domain modeling (encapsulation, polymorphism, abstraction), JDBC transactions, and concurrency control. |
| **Student 3: UI/UX & JavaFX Application Developer** | *Team Member 3* | JavaFX Scene Builder FXML views, controller event handling, client-side validation, and multi-role simulation views. |
| **Student 4: QA, Security & Deployment Engineer** | *Team Member 4* | BCrypt authentication, 5 dynamic business intelligence reports, JUnit test plan, and oral defense preparation. |

---

## 🏛️ System Architecture

ViaMe employs a decoupled **5-Tier Layered Architecture**:

```
Presentation (JavaFX / FXML)
       │
       ▼
Controllers (Event Handlers & Form Validation)
       │
       ▼
Service Layer (Business Logic, Dispatch Engine, Fare Strategies)
       │
       ▼
Data Access Layer (DAO Pattern with PreparedStatements)
       │
       ▼
Database Engine (MySQL 8.0 with InnoDB Storage Engine)
```

---

## 📊 Complete UML & Design Suite

All PlantUML diagram sources are maintained in the [`docs/uml/`](docs/uml/) directory:

| Diagram | Source File | Live Vector Rendering | Live Raster Rendering |
| :--- | :--- | :--- | :--- |
| **Conceptual ERD (3NF)** | [`ViaMe_ERD.puml`](docs/uml/ViaMe_ERD.puml) | [SVG View](https://www.plantuml.com/plantuml/svg/~1dLVTSgCs5BxdAJJfXPETP2Qx-PbsPsTdYS5PMXkxsCvChXW5P5iJG5GIITDDP_eGVS8-IOyam623iorNmFaa7RxpdH--28cvBD84tL4y8Q4RE9QaCY7c6VtprzzeoBA8vBB02G8pEiutBu96y4JJF24foIIMb6Me44GWaU574aPO42JnGqB4YMLjQ4nGIkI6nS8y24auOV8b9y8IZpJBCSSfIcX6r3l4E60R1h71CNkcsHgjS298mnAJ5IuIEMAPz75Aq9oi6K5BhnioftyGzFwyOGJdgNn1too4Rd3qkEQioE8XInX7FuoSqRLxeIoCnuINRo_qPVrfMUNQ8_XkVeImGCkv6-YjtY4Qe-tr4Trujvw_G9y-pNxv_1a0PsSQHL9CaotgpWw6N-tW-FtL-GbWbmRx3kKRbf5zs0T0jM1OY6TmDTnWiMdYhgvEsW214za0N5yQEsS9QJZj-il9yM1cp-UkV-i6WzE14tXt-iPs9fu_CAj0GR8GEwliuGAWW9ovlkFvjt0tNyxLW-kKwo9Ei2HnYALUvzWBT-5Dt2Qpoc6IhGaFSyvMD26Qvjci95aH7xQOtYNx58rQXA-A90ap9PJMYffueDHIhqhDX3b-KVeEZNZRt3STZJbzsl7IEUZb_t1ptyzta34HWUGCio9z87mVUDaM4lX8Lv2wAbt3EcvrfEjGsRDPCBs3I9qE0lTdTxWeWuOGXxNPEiHFwxPdZZlq9lRu-EBqW_4CFu6qyGDD53zxefhwOyzNyfYEHjMT7ou2RpPeyVf4DZGg2Rrpl_wdv2fZOi0DrAZEbmJqLr7NJgi6PodKiuuGN9OHI5bCEbAuCWlGkDYQmOF-d6dmNsMFvvUtBLOub51DIU0vRXSd1rZPffHUiSyBQXFN3IdZKc4wsUKqKbBXXS5rOcZqMEHXeaj0KqO_dLwVj13P-Wq4ZcDEXEWgUp5dELkjgcCwzgWHvfW3Y4F7K8YgIiR7jBt9TPKJSS5DwgKqwvV3LX21--lIdIzq7a8LzcvzVMkgSEZwOJ1TBa0c5kgz1dQWq8xMKWXPVHkuypay3QUJsTZLUnzQFhJzeJiUwmtyQLWUFPek_QgCS_9RGKHN8LSRu4YD3fLnrwg7ZzguQusOcY_wTeuKrqdIQTMLJgYPf9LvZHHQOMvIQ6G7FIbqA8jK1ePzPMW4zRmk7mym2ONgj0Qo4iRxyrePbNRKDqEppsGNIjAqWUZRInHyJS8KsXVD4wgwmXPrMM_594v2dCAu8_lTYWhEeR-q2dYb-ukJTgVMZTBqP_k-Xz7lvhEhM3ITxNEuxDCDZCcaeHqe1K-cDzxO3ITJtxtlcbvGLp6UsPvQExAzSTaHHyjwZ458Sfm97EcyvcJLOEZ7PYD1K7bfhVGTjJThkswmfhhRYtqUluV2BlPWt_wQRyoatomYLYdu8rfu_hsfN3f7qrgBrOTVdP_lZNWz0rwZIHZjT0xG1p_ym9nqNVgnbuHTsic17rEwtm2Pwdq0j9U17Gce5FD4MgVTJATZr_PD_S89g6N5MRezfuC_-EDIPKvJDuV2Nf8dsIF9Tdy3OChVuwunctQ5dVoUKsXtE_5JQDhLSI-lMZK15p6LOSACcksbyzrgHcyvPlAjCzge-gTh6ozRHhrrjV5XgGx09RXGG1BTlCM6vW8THvmz3mHQCIPF3CBIlt-lhsTdx5Nze-ZZZwX0U3jq7RL0JWjangucufk9gKBWGcwKwp2x4z76A68rPaqometTqbkN03KuQm1xpi0sQvZq0AbCPHS7izDbXcWygNtrLhj6Y5KCH83haBtxPjMm2l0AwPvhMTgWGA-lflig46WLGLSxsbeLYT18okEbX4wF7bwQW6-wGfRkHuHg3wql98kBDB7-1G) | [PNG View](https://www.plantuml.com/plantuml/png/~1dLVTSgCs5BxdAJJfXPETP2Qx-PbsPsTdYS5PMXkxsCvChXW5P5iJG5GIITDDP_eGVS8-IOyam623iorNmFaa7RxpdH--28cvBD84tL4y8Q4RE9QaCY7c6VtprzzeoBA8vBB02G8pEiutBu96y4JJF24foIIMb6Me44GWaU574aPO42JnGqB4YMLjQ4nGIkI6nS8y24auOV8b9y8IZpJBCSSfIcX6r3l4E60R1h71CNkcsHgjS298mnAJ5IuIEMAPz75Aq9oi6K5BhnioftyGzFwyOGJdgNn1too4Rd3qkEQioE8XInX7FuoSqRLxeIoCnuINRo_qPVrfMUNQ8_XkVeImGCkv6-YjtY4Qe-tr4Trujvw_G9y-pNxv_1a0PsSQHL9CaotgpWw6N-tW-FtL-GbWbmRx3kKRbf5zs0T0jM1OY6TmDTnWiMdYhgvEsW214za0N5yQEsS9QJZj-il9yM1cp-UkV-i6WzE14tXt-iPs9fu_CAj0GR8GEwliuGAWW9ovlkFvjt0tNyxLW-kKwo9Ei2HnYALUvzWBT-5Dt2Qpoc6IhGaFSyvMD26Qvjci95aH7xQOtYNx58rQXA-A90ap9PJMYffueDHIhqhDX3b-KVeEZNZRt3STZJbzsl7IEUZb_t1ptyzta34HWUGCio9z87mVUDaM4lX8Lv2wAbt3EcvrfEjGsRDPCBs3I9qE0lTdTxWeWuOGXxNPEiHFwxPdZZlq9lRu-EBqW_4CFu6qyGDD53zxefhwOyzNyfYEHjMT7ou2RpPeyVf4DZGg2Rrpl_wdv2fZOi0DrAZEbmJqLr7NJgi6PodKiuuGN9OHI5bCEbAuCWlGkDYQmOF-d6dmNsMFvvUtBLOub51DIU0vRXSd1rZPffHUiSyBQXFN3IdZKc4wsUKqKbBXXS5rOcZqMEHXeaj0KqO_dLwVj13P-Wq4ZcDEXEWgUp5dELkjgcCwzgWHvfW3Y4F7K8YgIiR7jBt9TPKJSS5DwgKqwvV3LX21--lIdIzq7a8LzcvzVMkgSEZwOJ1TBa0c5kgz1dQWq8xMKWXPVHkuypay3QUJsTZLUnzQFhJzeJiUwmtyQLWUFPek_QgCS_9RGKHN8LSRu4YD3fLnrwg7ZzguQusOcY_wTeuKrqdIQTMLJgYPf9LvZHHQOMvIQ6G7FIbqA8jK1ePzPMW4zRmk7mym2ONgj0Qo4iRxyrePbNRKDqEppsGNIjAqWUZRInHyJS8KsXVD4wgwmXPrMM_594v2dCAu8_lTYWhEeR-q2dYb-ukJTgVMZTBqP_k-Xz7lvhEhM3ITxNEuxDCDZCcaeHqe1K-cDzxO3ITJtxtlcbvGLp6UsPvQExAzSTaHHyjwZ458Sfm97EcyvcJLOEZ7PYD1K7bfhVGTjJThkswmfhhRYtqUluV2BlPWt_wQRyoatomYLYdu8rfu_hsfN3f7qrgBrOTVdP_lZNWz0rwZIHZjT0xG1p_ym9nqNVgnbuHTsic17rEwtm2Pwdq0j9U17Gce5FD4MgVTJATZr_PD_S89g6N5MRezfuC_-EDIPKvJDuV2Nf8dsIF9Tdy3OChVuwunctQ5dVoUKsXtE_5JQDhLSI-lMZK15p6LOSACcksbyzrgHcyvPlAjCzge-gTh6ozRHhrrjV5XgGx09RXGG1BTlCM6vW8THvmz3mHQCIPF3CBIlt-lhsTdx5Nze-ZZZwX0U3jq7RL0JWjangucufk9gKBWGcwKwp2x4z76A68rPaqometTqbkN03KuQm1xpi0sQvZq0AbCPHS7izDbXcWygNtrLhj6Y5KCH83haBtxPjMm2l0AwPvhMTgWGA-lflig46WLGLSxsbeLYT18okEbX4wF7bwQW6-wGfRkHuHg3wql98kBDB7-1G) |
| **Ride State Machine** | [`ViaMe_StateRide.puml`](docs/uml/ViaMe_StateRide.puml) | [SVG View](https://www.plantuml.com/plantuml/svg/~1VLHDZzCm4BtxLuo4X7QHkXmgSUW1xTAcgqfjiYJjNX3g6cUQcZfsP3kxL0Y97y4lv9SmJZ_J03aby-RvlPdC-DPvRdrTAdYKV8RBp7EFgSoHUUaLGlCDCoxMKYFy_laB0WXAhb1iXKB6t4RgYbjUWcjolpE03rni2cjgdG-DCXPUZKVZTr4_8CRcQFVHVlDGzCvQyz8E_c3iqvlFqEkzXpJwk8YoUJI20LJSETG5MX16hwGj7MCdF6JVPTdaFgQFmpE0tCfdOfZL2Ytcy1gu45Xvp2-eSR8SfPF7A5sEaqKy2bHjG72TopoKzXRwOF4h2Y-DTXVauLqyZARJat3BBDS2bOEhbKMyPkneCZ3tcb6yJ9F5F3gtl3FgsladtKw9EtUEd7fPegbzczdnICoTDNA62Bogb4H71YyzDPrDGt3K4kGUAYasTVLNnfdUluGEXuRqIRnyI9FxDCgoJcNDd3hmLbOjJhUaLkErwRcrURbKFLTgJabc3zFesDAZgZ1bfT2ZwpBQkWF04cs1McptXJ9sSIWqF1fgrl5y03e3U02ODZH_LXPhJsDyd75QdOXiRgajN3iPnXAaWsUkce7DmQ4blEV2GWDSXTtDA2G5Nj_0H2aik3eZKt_Nj8cKkiUQzPyVyUXR64TwkM6eSmYc_cCjeTHIEYy5A2CsGEQUo8qZ_XF1HSrjxY0tzHUwQEYFoKANgFtvsRVqIfSJ-mC) | [PNG View](https://www.plantuml.com/plantuml/png/~1VLHDZzCm4BtxLuo4X7QHkXmgSUW1xTAcgqfjiYJjNX3g6cUQcZfsP3kxL0Y97y4lv9SmJZ_J03aby-RvlPdC-DPvRdrTAdYKV8RBp7EFgSoHUUaLGlCDCoxMKYFy_laB0WXAhb1iXKB6t4RgYbjUWcjolpE03rni2cjgdG-DCXPUZKVZTr4_8CRcQFVHVlDGzCvQyz8E_c3iqvlFqEkzXpJwk8YoUJI20LJSETG5MX16hwGj7MCdF6JVPTdaFgQFmpE0tCfdOfZL2Ytcy1gu45Xvp2-eSR8SfPF7A5sEaqKy2bHjG72TopoKzXRwOF4h2Y-DTXVauLqyZARJat3BBDS2bOEhbKMyPkneCZ3tcb6yJ9F5F3gtl3FgsladtKw9EtUEd7fPegbzczdnICoTDNA62Bogb4H71YyzDPrDGt3K4kGUAYasTVLNnfdUluGEXuRqIRnyI9FxDCgoJcNDd3hmLbOjJhUaLkErwRcrURbKFLTgJabc3zFesDAZgZ1bfT2ZwpBQkWF04cs1McptXJ9sSIWqF1fgrl5y03e3U02ODZH_LXPhJsDyd75QdOXiRgajN3iPnXAaWsUkce7DmQ4blEV2GWDSXTtDA2G5Nj_0H2aik3eZKt_Nj8cKkiUQzPyVyUXR64TwkM6eSmYc_cCjeTHIEYy5A2CsGEQUo8qZ_XF1HSrjxY0tzHUwQEYFoKANgFtvsRVqIfSJ-mC) |
| **Driver Onboarding (AD-2)** | [`ViaMe_AD2_Onboarding.puml`](docs/uml/ViaMe_AD2_Onboarding.puml) | [SVG View](https://www.plantuml.com/plantuml/svg/~1ZL9DJyCm3BtdL_ZO2G7IZfiGL6i5HLjNZT7hb3NkPj4c9Kc7-6NS-MMuNVaOui39Zf_ztfET6-UbzMrTGKvoWPim6c-MUckaLQHtmfEl4CBeV0mGMJgW1VEDYdvOJ0R4jTkQl8C6hJDQLg2abt065HMe2voAoSFGSS0z5Koiq4kgd82WgQH7U7-3MZuTer5OTOb3wTreAgY406jkXqjezaPZ5_kfLjDpYzSZy7lK4BoY6mc0oSnYZpgsbiLfbAItN6O_ghUxQQmfYKsSO9_EXkAq8mfLJHei7WXV72XJj3LgxpgiCoKRPZgW-cs09q-LBZYdaWhfoMYuWZ3BLiiyZgR7va7wWfVgMzVXitMInmCwxBYG6dO6bkayINi8AuSGQFClnLLy7y_MNuhvRyKa_KjJ6yyag23WZV9bHxskLbIAd-gJXxrvWSBeaW_k0QqrjkC9eiTidip2TRo95s4ovxjbTyiqxkASgqnt97FUD4BSy8F_e_W0) | [PNG View](https://www.plantuml.com/plantuml/png/~1ZL9DJyCm3BtdL_ZO2G7IZfiGL6i5HLjNZT7hb3NkPj4c9Kc7-6NS-MMuNVaOui39Zf_ztfET6-UbzMrTGKvoWPim6c-MUckaLQHtmfEl4CBeV0mGMJgW1VEDYdvOJ0R4jTkQl8C6hJDQLg2abt065HMe2voAoSFGSS0z5Koiq4kgd82WgQH7U7-3MZuTer5OTOb3wTreAgY406jkXqjezaPZ5_kfLjDpYzSZy7lK4BoY6mc0oSnYZpgsbiLfbAItN6O_ghUxQQmfYKsSO9_EXkAq8mfLJHei7WXV72XJj3LgxpgiCoKRPZgW-cs09q-LBZYdaWhfoMYuWZ3BLiiyZgR7va7wWfVgMzVXitMInmCwxBYG6dO6bkayINi8AuSGQFClnLLy7y_MNuhvRyKa_KjJ6yyag23WZV9bHxskLbIAd-gJXxrvWSBeaW_k0QqrjkC9eiTidip2TRo95s4ovxjbTyiqxkASgqnt97FUD4BSy8F_e_W0) |
| **Pricing Rules (AD-3)** | [`ViaMe_AD3_Pricing.puml`](docs/uml/ViaMe_AD3_Pricing.puml) | [SVG View](https://www.plantuml.com/plantuml/svg/~1DL51RW8n3Bpd5NE4GoaLDn0jI0YfggYgLcgFADqrYwLiih8J4BU-fb_elK_fIngoS8hi6S-CdOL6Ap6r3kziDxHThYRR5-6AVMCYHqTOhcucmB9ksQCAViTD4b9qFGcI7AanHSPCUrheo2ikCj1AYFpi2bBDKV5fbTPM2BVeI9xQ_jra_1QQfA6PuHq61-kujf63HsTLIHyC0EiSxkSOu_Ud9-DkDCRVrpSce_4GSKyUWnFfC9EdAx9Lv8EDX2g9a8ye3IhHPsVAerUI24_7wrhRCuQB_rf2YpbyE0uB_KCuYzcK5zcwq21BogaWprb1kCxdIKhd74Mpzo6NMmCVIgYtVJY2lMDF894WPVoLD8Pyaqxem24fidqYpRE-vbs-SUYCMUGY_vVv1m) | [PNG View](https://www.plantuml.com/plantuml/png/~1DL51RW8n3Bpd5NE4GoaLDn0jI0YfggYgLcgFADqrYwLiih8J4BU-fb_elK_fIngoS8hi6S-CdOL6Ap6r3kziDxHThYRR5-6AVMCYHqTOhcucmB9ksQCAViTD4b9qFGcI7AanHSPCUrheo2ikCj1AYFpi2bBDKV5fbTPM2BVeI9xQ_jra_1QQfA6PuHq61-kujf63HsTLIHyC0EiSxkSOu_Ud9-DkDCRVrpSce_4GSKyUWnFfC9EdAx9Lv8EDX2g9a8ye3IhHPsVAerUI24_7wrhRCuQB_rf2YpbyE0uB_KCuYzcK5zcwq21BogaWprb1kCxdIKhd74Mpzo6NMmCVIgYtVJY2lMDF894WPVoLD8Pyaqxem24fidqYpRE-vbs-SUYCMUGY_vVv1m) |

For full specification and phase schedule, consult [VIAME_PROJECT_SPECIFICATION.md](VIAME_PROJECT_SPECIFICATION.md).
